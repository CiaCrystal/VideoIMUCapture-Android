package se.lth.math.videoimucapture;

/** Transient IME composition only; committed text is never accumulated. */
final class LetterInputTracker {
    private String composing = "";

    String update(CharSequence text, boolean commit) {
        String next = text == null ? "" : text.toString();
        int prefix = 0;
        while (prefix < composing.length() && prefix < next.length()
                && composing.charAt(prefix) == next.charAt(prefix)) prefix++;
        String added = next.substring(prefix);
        // Multi-character updates can be suggestions/paste/swipe, not individual key presses.
        String result = "";
        if (added.length() == 1) {
            char letter = added.charAt(0);
            if (letter >= 'a' && letter <= 'z') letter -= 'a' - 'A';
            if (letter >= 'A' && letter <= 'Z') result = String.valueOf(letter);
        }
        composing = commit ? "" : next;
        return result;
    }

    void reset() { composing = ""; }
    void delete() {
        if (!composing.isEmpty()) composing = composing.substring(0, composing.length() - 1);
    }
}
