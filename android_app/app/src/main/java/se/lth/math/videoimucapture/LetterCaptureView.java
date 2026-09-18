package se.lth.math.videoimucapture;

import android.content.Context;
import android.os.SystemClock;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

/** A focusable keyboard target that displays a fixed prompt, never the entered text. */
public class LetterCaptureView extends androidx.appcompat.widget.AppCompatTextView {
    interface Listener { void onLetter(String letter, long timeNs); }
    private Listener listener;
    private final LetterInputTracker tracker = new LetterInputTracker();
    public LetterCaptureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusableInTouchMode(true);
        setOnClickListener(v -> showKeyboard());
    }
    void setListener(Listener value) { listener = value; }
    void showKeyboard() {
        requestFocus();
        post(() -> {
            if (isShown() && isEnabled() && hasFocus()) {
                ((InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }
    void hideKeyboard() {
        tracker.reset();
        ((InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(getWindowToken(), 0);
        clearFocus();
    }
    private void input(CharSequence text, boolean commit) {
        long timeNs = SystemClock.elapsedRealtimeNanos();
        String letter = tracker.update(text, commit);
        if (isEnabled() && hasFocus() && listener != null && !letter.isEmpty()) {
            listener.onLetter(letter, timeNs);
        }
    }
    @Override public boolean onCheckIsTextEditor() { return isEnabled(); }
    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        if (!isEnabled()) return null;
        tracker.reset();
        info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        info.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_ACTION_DONE;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            info.imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }
        info.initialSelStart = info.initialSelEnd = 0;
        return new BaseInputConnection(this, false) {
            @Override public boolean commitText(CharSequence text, int position) {
                input(text, true); return true;
            }
            @Override public boolean setComposingText(CharSequence text, int position) {
                input(text, false); return true;
            }
            @Override public boolean finishComposingText() { tracker.reset(); return true; }
            @Override public boolean deleteSurroundingText(int before, int after) {
                if (before > 0) tracker.delete();
                return true;
            }
            @Override public boolean sendKeyEvent(KeyEvent event) {
                return handleKey(event);
            }
            @Override public boolean performEditorAction(int action) {
                hideKeyboard(); return true;
            }
        };
    }
    private boolean handleKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) tracker.delete();
            return true;
        }
        int unicode = event.getUnicodeChar();
        if ((unicode >= 'a' && unicode <= 'z') || (unicode >= 'A' && unicode <= 'Z')) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                input(String.valueOf((char) unicode), true);
            }
            return true;
        }
        return false;
    }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        return handleKey(event) || super.onKeyDown(code, event);
    }
    @Override public boolean onKeyUp(int code, KeyEvent event) {
        return handleKey(event) || super.onKeyUp(code, event);
    }
}
