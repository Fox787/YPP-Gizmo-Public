package main.java.general;

import javax.swing.JComponent;

/** Keep longer hover help readable without stretching across the screen. */
public final class HoverText {
    private HoverText() { }

    public static void set(JComponent component, String text) {
        if (text == null || text.isBlank()) {
            component.setToolTipText(null);
            return;
        }
        boolean html = text.startsWith("<html>");
        if (text.startsWith("<html><div style='width: 280px'>")
                || !html && text.length() <= 85 && !text.contains("\n")) {
            component.setToolTipText(text);
            return;
        }
        String body;
        if (html) {
            body = text.substring(6);
            if (body.endsWith("</html>")) body = body.substring(0, body.length() - 7);
        } else {
            body = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\n", "<br>");
        }
        component.setToolTipText("<html><div style='width: 280px'>" + body + "</div></html>");
    }
}
