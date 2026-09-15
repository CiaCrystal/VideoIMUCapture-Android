package se.lth.math.videoimucapture;

import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import se.lth.math.videoimucapture.RecordingProtos.VideoFrameMetaData;
import se.lth.math.videoimucapture.RecordingProtos.VideoFrameToTimestamp;
import se.lth.math.videoimucapture.RecordingProtos.IMUData;
import se.lth.math.videoimucapture.RecordingProtos.IMUInfo;
import se.lth.math.videoimucapture.RecordingProtos.CameraInfo;
import se.lth.math.videoimucapture.RecordingProtos.CaptureConfig;
import se.lth.math.videoimucapture.RecordingProtos.ExperimentInfo;
import se.lth.math.videoimucapture.RecordingProtos.MessageWrapper;
import se.lth.math.videoimucapture.RecordingProtos.TouchEvent;

import static java.lang.Math.abs;

public class RecordingWriter implements Runnable{
    private static final Logger LOG = Logger.getLogger("RecordingWriter");
    final private Boolean VERBOSE = false;

    private BufferedWriter mFileStream;
    private BlockingQueue<MessageWrapper> mQueue = new ArrayBlockingQueue<>(4096);
    private volatile boolean mAccepting;
    private volatile Exception mFailure;
    private Listener mListener;
    private String mResultFile;
    public interface Listener {
        void onClosed(String path, Exception error);
    }

    //Queues to handle merging of video frames
    private Queue<VideoFrameMetaData> mFrameDataQueue = new ArrayBlockingQueue<>(100);
    private Queue<VideoFrameToTimestamp> mFrameTimeQueue = new ArrayBlockingQueue<>(100);

    //Other state variables
    private volatile boolean mIsRecording = false;

    public Boolean isRecording() {return mIsRecording;}

    public void startRecording(String resultFile) throws IOException {
        startRecording(resultFile, null);
    }

    public synchronized void startRecording(String resultFile, Listener listener) throws IOException {
        if (mIsRecording) throw new IOException("Previous recording is still being saved");
        mListener = listener;
        mResultFile = resultFile;
        mFailure = null;

        LOG.fine(String.format("Starting on %s thread", Thread.currentThread()));
        mFileStream = openWriter(resultFile);

        //Reset state
        mIsRecording = true;
        mAccepting = true;
        mFrameDataQueue.clear();
        mFrameTimeQueue.clear();
        mQueue.clear();

        //Start background thread
        Thread myThread = new Thread(this, "RecordingWriter");
        myThread.start();

    }

    // Do not block the UI or sensor callback if storage is slow or the writer failed.
    BufferedWriter openWriter(String resultFile) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(resultFile), StandardCharsets.UTF_8));
    }

    public synchronized void stopRecording() {
        mAccepting = false;
    }

    public void run() {
        try {
            initializeFile();
            mFileStream.flush(); // Make even a very short recording a visible text file.
            long lastFlush = System.nanoTime();
            while (mAccepting || !mQueue.isEmpty()) {
                MessageWrapper msg = mQueue.poll(250, TimeUnit.MILLISECONDS);
                if (msg != null) writeMessage(msg);
                if (System.nanoTime() - lastFlush >= 1_000_000_000L) {
                    mFileStream.flush();
                    lastFlush = System.nanoTime();
                }
            }
            writeUnmatchedFrames();
            if (mFailure != null) {
                mFileStream.write("[RECORDING_ERROR]\nmessage=" + mFailure.getMessage() + "\n\n");
            }
        } catch (Exception e) {
            mFailure = e;
            LOG.severe("Recording write failed: " + e);
        } finally {
            synchronized (this) { mAccepting = false; }
            try {
                mFileStream.close();
            } catch (IOException e) {
                mFailure = e;
                LOG.severe("Could not close recording text file: " + e);
            }
            Listener listener;
            String path;
            Exception failure;
            synchronized (this) {
                listener = mListener;
                path = mResultFile;
                failure = mFailure;
                mIsRecording = false;
            }
            if (listener != null) listener.onClosed(path, failure);
        }
    }

    private void initializeFile() throws IOException {
        if (VERBOSE) LOG.fine(String.format("Initialize on %s thread", Thread.currentThread()));
        mFileStream.write(RecordingTextFormatter.header(System.currentTimeMillis()));
    }

    private void writeMessage(MessageWrapper msg) throws IOException {
        if (VERBOSE) LOG.fine(String.format("Queuing message on %s thread", Thread.currentThread()));

        MessageWrapper.MsgCase msgCase = msg.getMsgCase();
        switch (msgCase) {
            case FRAME_META:
                if (VERBOSE) LOG.fine("Got Frame Meta");
                if (mFrameDataQueue.size() == 100) {
                    mFileStream.write(RecordingTextFormatter.frame(mFrameDataQueue.poll(), false));
                }
                mFrameDataQueue.add(msg.getFrameMeta());
                tryVideoDataMerge();
                break;
            case FRAME_TIME:
                if (VERBOSE) LOG.fine("Got Frame Time");
                if (mFrameTimeQueue.size() == 100) {
                    mFileStream.write(RecordingTextFormatter.unmatchedFrameTime(mFrameTimeQueue.poll()));
                }
                mFrameTimeQueue.add(msg.getFrameTime());
                tryVideoDataMerge();
                break;
            case IMU_DATA:
                if (VERBOSE) LOG.fine("Got IMU data");
                mFileStream.write(RecordingTextFormatter.imu(msg.getImuData()));
                break;
            case IMU_META:
                if (VERBOSE) LOG.fine("Got IMU Info");
                mFileStream.write(RecordingTextFormatter.imuInfo(msg.getImuMeta()));
                break;
            case CAMERA_META:
                if (VERBOSE) LOG.fine("Got Camera Meta");
                mFileStream.write(RecordingTextFormatter.cameraInfo(msg.getCameraMeta()));
                break;
            case CAPTURE_CONFIG:
                mFileStream.write(RecordingTextFormatter.captureConfig(msg.getCaptureConfig()));
                break;
            case EXPERIMENT_INFO:
                mFileStream.write(RecordingTextFormatter.experimentInfo(msg.getExperimentInfo()));
                break;
            case TOUCH_EVENT:
                mFileStream.write(RecordingTextFormatter.touchEvent(msg.getTouchEvent()));
                break;
            case MSG_NOT_SET:
                break;
        }
    }


    private void tryVideoDataMerge() throws IOException {
        if (VERBOSE)  LOG.fine(String.format("Trying to merge, Queue lengths: %d,%d", mFrameDataQueue.size(), mFrameTimeQueue.size()));

        // Peek at oldest frame time message
        VideoFrameToTimestamp frameTimeMsg = mFrameTimeQueue.peek();
        VideoFrameMetaData frameMetaMsg = mFrameDataQueue.peek();

        //Try to find frames to match
        while ((frameTimeMsg != null) && (frameMetaMsg != null)) {
            long timeDiffNs = (1000*frameTimeMsg.getTimeUs() - frameMetaMsg.getTimeNs());
            if (VERBOSE) LOG.fine(String.format("Time diff: %d ns", timeDiffNs));

            if (abs(timeDiffNs) <= 10000) {
                // They are from the same capture frame
                VideoFrameMetaData.Builder frameBuilder = VideoFrameMetaData.newBuilder().mergeFrom(frameMetaMsg)
                        .setFrameNumber(frameTimeMsg.getFrameNbr());
                mFileStream.write(RecordingTextFormatter.frame(frameBuilder.build(), true));
                // Remove frames from queue
                mFrameTimeQueue.poll();
                mFrameDataQueue.poll();
                //We are done
                break;
            } else if (timeDiffNs > 0) {
                //Meta message is too old, try another one
                mFileStream.write(RecordingTextFormatter.frame(mFrameDataQueue.poll(), false));
                frameMetaMsg = mFrameDataQueue.peek();
                LOG.fine("Diff too large, saved unmatched frame meta data");
            } else {
                // Frame Time message too old, try another one
                mFileStream.write(RecordingTextFormatter.unmatchedFrameTime(mFrameTimeQueue.poll()));
                frameTimeMsg = mFrameTimeQueue.peek();
                LOG.fine("Diff too large, saved unmatched frame time data");
            }
        }

    }

    private void writeUnmatchedFrames() throws IOException {
        while (!mFrameDataQueue.isEmpty()) {
            mFileStream.write(RecordingTextFormatter.frame(mFrameDataQueue.poll(), false));
        }
        while (!mFrameTimeQueue.isEmpty()) {
            mFileStream.write(RecordingTextFormatter.unmatchedFrameTime(mFrameTimeQueue.poll()));
        }
    }

    private synchronized void queueData(MessageWrapper msg) {
        if (!mAccepting) return;
        if (!mQueue.offer(msg)) {
            mFailure = new IOException("Recording queue full; storage cannot keep up. Recording is incomplete.");
            mAccepting = false;
        }
    }
    public void queueData(VideoFrameMetaData msg) {
        queueData(MessageWrapper.newBuilder().setFrameMeta(msg).build());
    }
    public void queueData(VideoFrameToTimestamp msg) {
        queueData(MessageWrapper.newBuilder().setFrameTime(msg).build());
    }
    public void queueData(IMUData msg) {
        queueData(MessageWrapper.newBuilder().setImuData(msg).build());
    }
    public void queueData(IMUInfo msg) {
        queueData(MessageWrapper.newBuilder().setImuMeta(msg).build());
    }
    public void queueData(CameraInfo msg) {
        queueData(MessageWrapper.newBuilder().setCameraMeta(msg).build());
    }
    public void queueData(CaptureConfig msg) {
        queueData(MessageWrapper.newBuilder().setCaptureConfig(msg).build());
    }
    public void queueData(ExperimentInfo msg) {
        queueData(MessageWrapper.newBuilder().setExperimentInfo(msg).build());
    }
    public void queueData(TouchEvent msg) {
        queueData(MessageWrapper.newBuilder().setTouchEvent(msg).build());
    }

}
