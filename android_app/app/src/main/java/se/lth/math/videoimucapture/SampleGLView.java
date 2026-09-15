package se.lth.math.videoimucapture;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by sudamasayuki on 2018/03/14.
 */

public class SampleGLView extends GLSurfaceView implements View.OnTouchListener {

    public SampleGLView(Context context) {
        this(context, null);
    }

    public SampleGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnTouchListener(this);
    }

    private TouchListener touchListener;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int actionMasked = event.getActionMasked();
        if (actionMasked != MotionEvent.ACTION_DOWN
                && actionMasked != MotionEvent.ACTION_UP
                && actionMasked != MotionEvent.ACTION_POINTER_DOWN
                && actionMasked != MotionEvent.ACTION_POINTER_UP
                && actionMasked != MotionEvent.ACTION_CANCEL) {
            return true;
        }

        if (touchListener != null) {
            touchListener.onTouch(event, v.getWidth(), v.getHeight());
        }
        // Consume DOWN so Android continues delivering the matching UP event.
        return true;
    }

    public interface TouchListener {
        void onTouch(MotionEvent event, int width, int height);
    }

    public void setTouchListener(TouchListener touchListener) {
        this.touchListener = touchListener;
    }
}

