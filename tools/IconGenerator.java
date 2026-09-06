import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws the RamWatch app icon and writes it out as a PNG.
 *
 * <p>The icon is a memory chip on a graphite squircle: the chip outline and its pins say
 * "RAM", the three bars inside it are the usage meter the app is built around, and their
 * colours are the stable/warning/critical greens and ambers of the dashboard.
 *
 * <p>Run it with {@code java tools/IconGenerator.java <size> <output.png>}; the drawing is
 * defined in fractions of the canvas, so any size renders the same picture.
 */
public final class IconGenerator {

    public static void main(String[] args) throws Exception {
        int size = args.length > 0 ? Integer.parseInt(args[0]) : 1024;
        String out = args.length > 1 ? args[1] : "icon.png";

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double s = size;
        // macOS icons leave a margin around the artwork so they sit right in the dock.
        double pad = s * 0.085;
        double box = s - 2 * pad;

        // Background squircle.
        RoundRectangle2D bg = new RoundRectangle2D.Double(pad, pad, box, box, box * 0.46, box * 0.46);
        g.setPaint(new GradientPaint(0, (float) pad, new Color(0x33, 0x38, 0x40),
                                     0, (float) (pad + box), new Color(0x16, 0x18, 0x1C)));
        g.fill(bg);

        // Hairline highlight along the top edge, the way a glass surface catches light.
        g.setPaint(new Color(255, 255, 255, 38));
        g.setStroke(new BasicStroke((float) (s * 0.006)));
        g.draw(new RoundRectangle2D.Double(pad, pad, box, box, box * 0.46, box * 0.46));

        // Chip outline.
        double chip = box * 0.50;
        double chipX = pad + (box - chip) / 2;
        double chipY = pad + (box - chip) / 2;
        double stroke = s * 0.042;
        g.setPaint(new Color(0xF5, 0xF5, 0xF7));
        g.setStroke(new BasicStroke((float) stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new RoundRectangle2D.Double(chipX, chipY, chip, chip, chip * 0.22, chip * 0.22));

        // Pins on the four sides, three per side.
        double pin = box * 0.075;
        g.setStroke(new BasicStroke((float) (stroke * 0.8), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            double at = chip * (0.25 + 0.25 * i);
            g.draw(new Line2D.Double(chipX + at, chipY, chipX + at, chipY - pin));
            g.draw(new Line2D.Double(chipX + at, chipY + chip, chipX + at, chipY + chip + pin));
            g.draw(new Line2D.Double(chipX, chipY + at, chipX - pin, chipY + at));
            g.draw(new Line2D.Double(chipX + chip, chipY + at, chipX + chip + pin, chipY + at));
        }

        // Usage bars inside the chip, growing left to right like a filling meter.
        Color[] bars = { new Color(0x30, 0xD1, 0x58), new Color(0x30, 0xD1, 0x58), new Color(0xFF, 0xD6, 0x0A) };
        double[] heights = { 0.34, 0.55, 0.74 };
        double barW = chip * 0.14;
        double gap = (chip - 3 * barW) / 4;
        double baseY = chipY + chip - chip * 0.20;
        for (int i = 0; i < 3; i++) {
            double h = chip * heights[i];
            double x = chipX + gap + i * (barW + gap);
            g.setPaint(bars[i]);
            g.fill(new RoundRectangle2D.Double(x, baseY - h, barW, h, barW, barW));
        }

        g.dispose();
        File file = new File(out);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        ImageIO.write(image, "png", file);
        System.out.println("wrote " + file.getPath() + " (" + size + "px)");
    }
}
