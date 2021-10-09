package cuchaz.enigma.gui.util;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import cuchaz.enigma.gui.Gui;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.Os;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.NoSuchElementException;

public class GuiUtil {
    public static final Icon CLASS_ICON = loadIcon("class");
    public static final Icon INTERFACE_ICON = loadIcon("interface");
    public static final Icon ENUM_ICON = loadIcon("enum");
    public static final Icon ANNOTATION_ICON = loadIcon("annotation");
    public static final Icon RECORD_ICON = loadIcon("record");
    public static final Icon METHOD_ICON = loadIcon("method");
    public static final Icon FIELD_ICON = loadIcon("field");
    public static final Icon CONSTRUCTOR_ICON = loadIcon("constructor");

    public static void openUrl(String url) {
        try {
            switch (Os.getOs()) {
                case LINUX:
                    new ProcessBuilder("/usr/bin/env", "xdg-open", url).start();
                    break;
                default:
                    if (Desktop.isDesktopSupported()) {
                        Desktop desktop = Desktop.getDesktop();
                        desktop.browse(new URI(url));
                    }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public static JLabel unboldLabel(JLabel label) {
        Font font = label.getFont();
        label.setFont(font.deriveFont(font.getStyle() & ~Font.BOLD));
        return label;
    }

    /**
     * Puts the provided {@code text} in the system clipboard.
     */
    public static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    public static void showPopup(JComponent component, String text, int x, int y) {
        // from https://stackoverflow.com/questions/39955015/java-swing-show-tooltip-as-a-message-dialog
        JToolTip tooltip = new JToolTip();
        tooltip.setTipText(text);
        Popup p = PopupFactory.getSharedInstance().getPopup(component, tooltip, x + 10, y);
        p.show();
        Timer t = new Timer(1000, e -> p.hide());
        t.setRepeats(false);
        t.start();
    }

    public static void showToolTipNow(JComponent component) {
        // HACKHACK: trick the tooltip manager into showing the tooltip right now
        ToolTipManager manager = ToolTipManager.sharedInstance();
        int oldDelay = manager.getInitialDelay();
        manager.setInitialDelay(0);
        manager.mouseMoved(new MouseEvent(component, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 0, 0, 0, false));
        manager.setInitialDelay(oldDelay);
    }

    public static JLabel createLink(String text, Runnable action) {
        JLabel link = new JLabel(text);
        link.setForeground(Color.BLUE.darker());
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        @SuppressWarnings("unchecked")
        Map<TextAttribute, Object> attributes = (Map<TextAttribute, Object>) link.getFont().getAttributes();
        attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        link.setFont(link.getFont().deriveFont(attributes));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                action.run();
            }
        });
        return link;
    }

    public static Icon loadIcon(String name) {
        String path = "icons/" + name + ".svg";

        // Do an eager check for a missing icon since FlatSVGIcon does it later at render time
        if (GuiUtil.class.getResource('/' + path) == null) {
            throw new NoSuchElementException("Missing icon: '" + name + "' at " + path);
        }

        // Note: the width and height are scaled automatically because the FlatLaf UI scale
        // is set in LookAndFeel.setGlobalLAF()
        return new FlatSVGIcon(path, 16, 16, GuiUtil.class.getClassLoader());
    }

    public static Icon getClassIcon(Gui gui, ClassEntry entry) {
        AccessFlags access = gui.getController().project.getJarIndex().getEntryIndex().getClassAccess(entry);

        if (access != null) {
            if (access.isAnnotation()) {
                return ANNOTATION_ICON;
            } else if (access.isInterface()) {
                return INTERFACE_ICON;
            } else if (access.isEnum()) {
                return ENUM_ICON;
            } else if (access.isRecord()) {
                return RECORD_ICON;
            }
        }

        return CLASS_ICON;
    }

    public static Icon getMethodIcon(MethodEntry entry) {
        if (entry.isConstructor()) {
            return CONSTRUCTOR_ICON;
        }
        return METHOD_ICON;
    }
}
