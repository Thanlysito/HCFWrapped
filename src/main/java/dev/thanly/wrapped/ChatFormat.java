package dev.thanly.wrapped;

import org.bukkit.ChatColor;

/**
 * Ayudas para que el chat se vea ordenado: centrar texto y alinear columnas.
 * La fuente de Minecraft no es monoespaciada, asi que se mide en pixeles aproximados.
 */
public final class ChatFormat {

    /** Mitad del ancho del chat por defecto, en pixeles. */
    private static final int CENTER_PX = 154;

    private ChatFormat() {
    }

    /** Ancho de un caracter en la fuente de Minecraft (+1 de separacion). */
    private static int width(char c, boolean bold) {
        int w;
        switch (c) {
            case 'i': case '!': case '.': case ',': case ':': case ';': case '|': case '\'':
                w = 2;
                break;
            case 'l': case '`':
                w = 3;
                break;
            case 'I': case 't': case '[': case ']': case ' ':
                w = 4;
                break;
            case 'f': case 'k': case '<': case '>': case '(': case ')': case '{': case '}': case '"': case '*':
                w = 5;
                break;
            case '@': case '~':
                w = 7;
                break;
            default:
                w = c > 127 ? 8 : 6; // simbolos unicode (⚔ ☠ ❤...) son un poco mas anchos
        }
        return bold && c != ' ' ? w + 1 : w;
    }

    /** Ancho en pixeles de un texto con codigos de color (§). */
    public static int width(String text) {
        int px = 0;
        boolean code = false;
        boolean bold = false;
        for (char c : text.toCharArray()) {
            if (c == ChatColor.COLOR_CHAR) {
                code = true;
                continue;
            }
            if (code) {
                code = false;
                char l = Character.toLowerCase(c);
                if (l == 'l') {
                    bold = true;
                } else if (l == 'r' || (l >= '0' && l <= '9') || (l >= 'a' && l <= 'f')) {
                    bold = false;
                }
                continue;
            }
            px += width(c, bold);
        }
        return px;
    }

    /** Espacios para que el texto quede centrado en el chat. */
    public static String center(String colored) {
        int half = width(colored) / 2;
        int pad = CENTER_PX - half;
        return spaces(pad) + colored;
    }

    /** Rellena con espacios hasta llegar a cierto ancho (para hacer columnas). */
    public static String padTo(String colored, int px) {
        return colored + spaces(px - width(colored));
    }

    private static String spaces(int px) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < px; i += 4) {
            b.append(' ');
        }
        return b.toString();
    }

    /** Linea separadora tachada, del ancho del chat. */
    public static String bar(String color) {
        return Messages.color(color + "&m" + spaces(CENTER_PX * 2 - 10) );
    }
}
