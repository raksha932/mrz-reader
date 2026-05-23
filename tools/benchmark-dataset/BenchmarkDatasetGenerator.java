import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

public final class BenchmarkDatasetGenerator {
    private static final int DEFAULT_SEED = 9303;
    private static final int DEFAULT_COPIES = 4;
    private static final String VERSION = "1.0.0";

    private final Path outputDir;
    private final int copiesPerScenario;
    private final long seed;
    private final Random random;
    private final List<ManifestCase> manifestCases = new ArrayList<>();

    private BenchmarkDatasetGenerator(Path outputDir, int copiesPerScenario, long seed) {
        this.outputDir = outputDir;
        this.copiesPerScenario = copiesPerScenario;
        this.seed = seed;
        this.random = new Random(seed);
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseOptions(args);
        Path output = Path.of(options.getOrDefault("output", "benchmark-dataset/generated"));
        int copies = Integer.parseInt(options.getOrDefault("copies", String.valueOf(DEFAULT_COPIES)));
        long seed = Long.parseLong(options.getOrDefault("seed", String.valueOf(DEFAULT_SEED)));

        BenchmarkDatasetGenerator generator = new BenchmarkDatasetGenerator(output, copies, seed);
        generator.generate();
        System.out.printf(Locale.ROOT, "Generated %d benchmark images in %s%n",
                generator.manifestCases.size(), output.toAbsolutePath());
    }

    private void generate() throws IOException {
        Files.createDirectories(outputDir.resolve("images"));

        List<BaseDocument> bases = new ArrayList<>();
        bases.addAll(td3Documents());
        bases.addAll(td2Documents());
        bases.addAll(td1Documents());
        bases.addAll(mrvDocuments());

        for (BaseDocument base : bases) {
            for (Scenario scenario : scenarios()) {
                for (int copy = 0; copy < copiesPerScenario; copy++) {
                    renderCase(base, scenario, copy);
                }
            }
        }

        writeManifest();
    }

    private void renderCase(BaseDocument base, Scenario scenario, int copy) throws IOException {
        BufferedImage image = renderBaseImage(base, copy);
        image = scenario.apply(image, random);

        String id = "%s_%s_%03d".formatted(
                base.id(), scenario.name().toLowerCase(Locale.ROOT).replace('-', '_'), copy + 1);
        Path relativeImagePath = Path.of("images", base.format().toLowerCase(Locale.ROOT), id + ".png");
        Path imagePath = outputDir.resolve(relativeImagePath);
        Files.createDirectories(imagePath.getParent());
        ImageIO.write(image, "png", imagePath.toFile());

        manifestCases.add(new ManifestCase(id, relativeImagePath.toString().replace('\\', '/'), base, scenario));
    }

    private BufferedImage renderBaseImage(BaseDocument base, int copy) {
        DocumentCanvas canvas = canvasFor(base.format());
        BufferedImage image = new BufferedImage(canvas.width(), canvas.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        configureGraphics(graphics);

        graphics.setColor(new Color(238, 241, 236));
        graphics.fillRect(0, 0, canvas.width(), canvas.height());
        graphics.setPaint(new GradientPaint(0, 0, new Color(250, 250, 246),
                canvas.width(), canvas.height(), new Color(218, 224, 221)));
        graphics.fillRoundRect(48, 36, canvas.width() - 96, canvas.height() - 72, 28, 28);

        graphics.setColor(new Color(185, 196, 202));
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.drawRoundRect(48, 36, canvas.width() - 96, canvas.height() - 72, 28, 28);

        graphics.setColor(new Color(197, 207, 214));
        graphics.fillRoundRect(86, 96, 220, 250, 18, 18);
        graphics.setColor(new Color(130, 146, 155));
        graphics.drawOval(151, 134, 90, 90);
        graphics.drawRoundRect(132, 238, 130, 82, 28, 28);

        graphics.setColor(new Color(170, 181, 188));
        for (int i = 0; i < 7; i++) {
            int y = 98 + i * 32;
            graphics.drawLine(350, y, canvas.width() - 120 - random.nextInt(40), y);
        }

        int stripX = 76;
        int stripY = canvas.mrzTop();
        int stripWidth = canvas.width() - 152;
        int stripHeight = canvas.height() - canvas.mrzTop() - 70;
        graphics.setColor(new Color(250, 250, 247));
        graphics.fillRect(stripX, stripY, stripWidth, stripHeight);
        graphics.setColor(new Color(44, 48, 50));
        graphics.setStroke(new BasicStroke(1.3f));
        graphics.drawRect(stripX, stripY, stripWidth, stripHeight);

        int jitterX = (copy % 3) - 1;
        int jitterY = ((copy / 2) % 3) - 1;
        drawMrzLines(graphics, base.lines(), stripX + 26 + jitterX, stripY + 26 + jitterY,
                stripWidth - 52, stripHeight - 38);

        graphics.dispose();
        return image;
    }

    private void drawMrzLines(Graphics2D graphics, List<String> lines, int x, int y, int width, int height) {
        int lineCount = lines.size();
        int maxChars = lines.stream().mapToInt(String::length).max().orElse(44);
        int fontSize = Math.min(36, Math.max(24, (int) Math.floor(width / (maxChars * 0.66))));
        Font font = new Font(Font.MONOSPACED, Font.BOLD, fontSize);
        graphics.setFont(font);
        graphics.setColor(Color.BLACK);
        FontMetrics metrics = graphics.getFontMetrics(font);
        double charAdvance = (double) width / maxChars;
        int lineHeight = Math.min(metrics.getHeight() + 8, height / lineCount);
        int baseline = y + metrics.getAscent();

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            int currentY = baseline + lineIndex * lineHeight;
            for (int charIndex = 0; charIndex < line.length(); charIndex++) {
                int currentX = x + (int) Math.round(charIndex * charAdvance);
                graphics.drawString(String.valueOf(line.charAt(charIndex)), currentX, currentY);
            }
        }
    }

    private void writeManifest() throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"dataset_version\": \"").append(VERSION).append("\",\n");
        json.append("  \"generator\": \"BenchmarkDatasetGenerator\",\n");
        json.append("  \"generated_at\": \"").append(Instant.now()).append("\",\n");
        json.append("  \"seed\": ").append(seed).append(",\n");
        json.append("  \"case_count\": ").append(manifestCases.size()).append(",\n");
        json.append("  \"cases\": [\n");
        for (int i = 0; i < manifestCases.size(); i++) {
            json.append(manifestCases.get(i).toJson("    "));
            if (i < manifestCases.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        Files.writeString(outputDir.resolve("manifest.json"), json.toString(), StandardCharsets.UTF_8);
    }

    private static List<Scenario> scenarios() {
        return List.of(
                new Scenario("clean", "VALID_MRZ", null, BenchmarkDatasetGenerator::identity),
                new Scenario("skew-left", "VALID_MRZ", null, (image, random) -> rotate(image, -3.2)),
                new Scenario("skew-right", "VALID_MRZ", null, (image, random) -> rotate(image, 3.0)),
                new Scenario("blur-light", "VALID_MRZ", null, (image, random) -> blur(image, 3)),
                new Scenario("blur-heavy", "LOW_QUALITY", "IMAGE_QUALITY_LOW", (image, random) -> blur(image, 7)),
                new Scenario("low-contrast", "VALID_MRZ", null, BenchmarkDatasetGenerator::lowContrast),
                new Scenario("low-resolution", "VALID_MRZ", null, BenchmarkDatasetGenerator::lowResolution),
                new Scenario("glare", "VALID_MRZ", null, BenchmarkDatasetGenerator::glare),
                new Scenario("partial-occlusion", "VALID_MRZ", null, BenchmarkDatasetGenerator::partialOcclusion));
    }

    private static BufferedImage identity(BufferedImage image, Random random) {
        return image;
    }

    private static BufferedImage lowContrast(BufferedImage image, Random random) {
        BufferedImage output = copy(image);
        for (int y = 0; y < output.getHeight(); y++) {
            for (int x = 0; x < output.getWidth(); x++) {
                Color color = new Color(output.getRGB(x, y));
                int red = soften(color.getRed());
                int green = soften(color.getGreen());
                int blue = soften(color.getBlue());
                output.setRGB(x, y, new Color(red, green, blue).getRGB());
            }
        }
        return output;
    }

    private static int soften(int value) {
        return Math.max(0, Math.min(255, 128 + (int) ((value - 128) * 0.48)));
    }

    private static BufferedImage lowResolution(BufferedImage image, Random random) {
        int smallWidth = image.getWidth() / 3;
        int smallHeight = image.getHeight() / 3;
        BufferedImage small = new BufferedImage(smallWidth, smallHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D gSmall = small.createGraphics();
        gSmall.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gSmall.drawImage(image, 0, 0, smallWidth, smallHeight, null);
        gSmall.dispose();

        BufferedImage output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D gOutput = output.createGraphics();
        gOutput.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gOutput.drawImage(small, 0, 0, output.getWidth(), output.getHeight(), null);
        gOutput.dispose();
        return output;
    }

    private static BufferedImage glare(BufferedImage image, Random random) {
        BufferedImage output = copy(image);
        Graphics2D graphics = output.createGraphics();
        configureGraphics(graphics);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.42f));
        graphics.setColor(Color.WHITE);
        graphics.fillOval(output.getWidth() / 2 - 90, output.getHeight() / 2 - 170, 430, 280);
        graphics.dispose();
        return output;
    }

    private static BufferedImage partialOcclusion(BufferedImage image, Random random) {
        BufferedImage output = copy(image);
        Graphics2D graphics = output.createGraphics();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f));
        graphics.setColor(new Color(235, 237, 226));
        graphics.fillRoundRect(output.getWidth() - 360, output.getHeight() - 166, 220, 44, 8, 8);
        graphics.dispose();
        return output;
    }

    private static BufferedImage blur(BufferedImage image, int size) {
        float[] kernel = new float[size * size];
        float value = 1.0f / kernel.length;
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] = value;
        }
        ConvolveOp op = new ConvolveOp(new Kernel(size, size, kernel), ConvolveOp.EDGE_NO_OP, null);
        return op.filter(image, null);
    }

    private static BufferedImage rotate(BufferedImage image, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        int width = image.getWidth();
        int height = image.getHeight();
        int newWidth = (int) Math.floor(width * cos + height * sin);
        int newHeight = (int) Math.floor(height * cos + width * sin);

        BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rotated.createGraphics();
        graphics.setColor(new Color(238, 241, 236));
        graphics.fillRect(0, 0, newWidth, newHeight);
        graphics.dispose();

        AffineTransform transform = new AffineTransform();
        transform.translate((newWidth - width) / 2.0, (newHeight - height) / 2.0);
        transform.rotate(radians, width / 2.0, height / 2.0);
        AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
        op.filter(image, rotated);
        return rotated;
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return output;
    }

    private static void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static DocumentCanvas canvasFor(String format) {
        return switch (format) {
            case "TD1" -> new DocumentCanvas(960, 620, 424);
            case "TD2" -> new DocumentCanvas(1040, 640, 464);
            case "MRV", "TD3" -> new DocumentCanvas(1200, 760, 570);
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        };
    }

    private static List<BaseDocument> td3Documents() {
        return List.of(
                document("td3_uto_eriksson", "TD3", "PASSPORT", "L898902C3", "UTO", "UTO", "ERIKSSON", "ANNA MARIA", true,
                        List.of("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<", "L898902C36UTO7408122F1204159ZE184226B<<<<<10")),
                document("td3_nga_okonkwo", "TD3", "PASSPORT", "A12345678", "NGA", "NGA", "OKONKWO", "CHUKWUEMEKA JOHN", false,
                        List.of("P<NGAOKONKWO<<CHUKWUEMEKA<JOHN<<<<<<<<<<<<<<", "A123456784NGA9008157M2908144<<<<<<<<<<<<<<06")),
                document("td3_gbr_smith", "TD3", "PASSPORT", "123456789", "GBR", "GBR", "SMITH", "JOHN JAMES", false,
                        List.of("P<GBRSMITH<<JOHN<JAMES<<<<<<<<<<<<<<<<<<<<<<", "1234567897GBR8001014M3001019<<<<<<<<<<<<<<08")),
                document("td3_usa_doe", "TD3", "PASSPORT", "987654321", "USA", "USA", "DOE", "JANE ALICE", false,
                        List.of("P<USADOE<<JANE<ALICE<<<<<<<<<<<<<<<<<<<<<<<<", "9876543213USA8505051F3105054<<<<<<<<<<<<<<00")),
                document("td3_can_martin", "TD3", "PASSPORT", "AB1234567", "CAN", "CAN", "MARTIN", "EMMA ROSE", false,
                        List.of("P<CANMARTIN<<EMMA<ROSE<<<<<<<<<<<<<<<<<<<<<<", "AB12345671CAN9202209F3202207<<<<<<<<<<<<<<02")));
    }

    private static List<BaseDocument> td2Documents() {
        return List.of(
                document("td2_uto_eriksson", "TD2", "OFFICIAL_TRAVEL_DOCUMENT", "D0012345", "UTO", "UTO", "ERIKSSON", "ANNA MARIA", true,
                        List.of("I<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<", "D0012345<0UTO7408122F1204159<<<<<<<0")),
                document("td2_nga_okonkwo", "TD2", "OFFICIAL_TRAVEL_DOCUMENT", "ABC12345", "NGA", "NGA", "OKONKWO", "CHUKWUEMEKA", false,
                        List.of("I<NGAOKONKWO<<CHUKWUEMEKA<<<<<<<<<<<", "ABC12345<4NGA9008157M3008144<<<<<<<4")),
                document("td2_gbr_smith", "TD2", "OFFICIAL_TRAVEL_DOCUMENT", "12345678", "GBR", "GBR", "SMITH", "JOHN JAMES", false,
                        List.of("I<GBRSMITH<<JOHN<JAMES<<<<<<<<<<<<<<", "12345678<8GBR8001014M3001019<<<<<<<6")),
                document("td2_usa_doe", "TD2", "OFFICIAL_TRAVEL_DOCUMENT", "98765432", "USA", "USA", "DOE", "JANE ALICE", false,
                        List.of("I<USADOE<<JANE<ALICE<<<<<<<<<<<<<<<<", "98765432<2USA8505051F3105054<<<<<<<2")),
                document("td2_can_martin", "TD2", "OFFICIAL_TRAVEL_DOCUMENT", "AB123456", "CAN", "CAN", "MARTIN", "EMMA ROSE", false,
                        List.of("I<CANMARTIN<<EMMA<ROSE<<<<<<<<<<<<<<", "AB123456<4CAN9202209F3202207<<<<<<<6")));
    }

    private static List<BaseDocument> td1Documents() {
        return List.of(
                document("td1_uto_eriksson", "TD1", "ID_CARD", "D23145890", "UTO", "UTO", "ERIKSSON", "ANNA MARIA", true,
                        List.of("I<UTOD231458907<<<<<<<<<<<<<<<", "7408122F1204159UTO<<<<<<<<<<<6", "ERIKSSON<<ANNA<MARIA<<<<<<<<<<")),
                document("td1_nga_okonkwo", "TD1", "ID_CARD", "ABC123456", "NGA", "NGA", "OKONKWO", "CHUKWUEMEKA JOHN", false,
                        List.of("I<NGAABC1234560<<<<<<<<<<<<<<<", "9008157M3008144NGA<<<<<<<<<<<2", "OKONKWO<<CHUKWUEMEKA<JOHN<<<<<")),
                document("td1_gbr_smith", "TD1", "ID_CARD", "123456789", "GBR", "GBR", "SMITH", "JOHN JAMES", false,
                        List.of("I<GBR1234567897<<<<<<<<<<<<<<<", "8001014M3001019GBR<<<<<<<<<<<8", "SMITH<<JOHN<JAMES<<<<<<<<<<<<<")),
                document("td1_usa_doe", "TD1", "ID_CARD", "987654321", "USA", "USA", "DOE", "JANE ALICE", false,
                        List.of("I<USA9876543213<<<<<<<<<<<<<<<", "8505051F3105054USA<<<<<<<<<<<0", "DOE<<JANE<ALICE<<<<<<<<<<<<<<<")),
                document("td1_can_martin", "TD1", "ID_CARD", "AB1234567", "CAN", "CAN", "MARTIN", "EMMA ROSE", false,
                        List.of("I<CANAB12345671<<<<<<<<<<<<<<<", "9202209F3202207CAN<<<<<<<<<<<2", "MARTIN<<EMMA<ROSE<<<<<<<<<<<<<")));
    }

    private static List<BaseDocument> mrvDocuments() {
        return List.of(
                document("mrv_uto_visa", "MRV", "VISA", "V12345678", "UTO", "UTO", "TRAVELER", "TEST ALPHA", false,
                        List.of("V<UTOTRAVELER<<TEST<ALPHA<<<<<<<<<<<<<<<<<<", "V123456785UTO8001014F3001019<<<<<<<<<<<<<<06")));
    }

    private static BaseDocument document(
            String id,
            String format,
            String documentType,
            String documentNumber,
            String issuingState,
            String nationality,
            String surname,
            String givenNames,
            boolean expired,
            List<String> lines) {
        return new BaseDocument(
                id, format, documentType, documentNumber, issuingState, nationality, surname, givenNames, expired, lines);
    }

    private static Map<String, String> parseOptions(String[] args) {
        java.util.LinkedHashMap<String, String> options = new java.util.LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Expected --name=value option but got: " + arg);
            }
            int equalsIndex = arg.indexOf('=');
            options.put(arg.substring(2, equalsIndex), arg.substring(equalsIndex + 1));
        }
        return options;
    }

    private record DocumentCanvas(int width, int height, int mrzTop) {
    }

    @FunctionalInterface
    private interface ImageEffect {
        BufferedImage apply(BufferedImage image, Random random);
    }

    private record Scenario(String name, String expectedKind, String errorCode, ImageEffect effect) {
        BufferedImage apply(BufferedImage image, Random random) {
            return effect.apply(image, random);
        }
    }

    private record BaseDocument(
            String id,
            String format,
            String documentType,
            String documentNumber,
            String issuingState,
            String nationality,
            String surname,
            String givenNames,
            boolean expired,
            List<String> lines) {
    }

    private record ManifestCase(String id, String imagePath, BaseDocument document, Scenario scenario) {
        String toJson(String indent) {
            StringBuilder json = new StringBuilder();
            json.append(indent).append("{\n");
            json.append(indent).append("  \"id\": \"").append(escape(id)).append("\",\n");
            json.append(indent).append("  \"image_path\": \"").append(escape(imagePath)).append("\",\n");
            json.append(indent).append("  \"content_type\": \"image/png\",\n");
            json.append(indent).append("  \"format\": \"").append(document.format()).append("\",\n");
            json.append(indent).append("  \"scenario\": \"").append(scenario.name()).append("\",\n");
            json.append(indent).append("  \"expected_outcome\": {\n");
            String expectedKind = "MRV".equals(document.format()) ? "UNSUPPORTED_FORMAT" : scenario.expectedKind();
            String errorCode = "MRV".equals(document.format()) ? "UNSUPPORTED_FORMAT" : scenario.errorCode();
            json.append(indent).append("    \"kind\": \"").append(expectedKind).append("\"");
            if (errorCode != null) {
                json.append(",\n").append(indent).append("    \"error_code\": \"").append(errorCode).append("\"\n");
                json.append(indent).append("  },\n");
            } else {
                json.append("\n").append(indent).append("  },\n");
            }
            json.append(indent).append("  \"ground_truth\": {\n");
            json.append(indent).append("    \"mrz_lines\": [");
            for (int i = 0; i < document.lines().size(); i++) {
                if (i > 0) {
                    json.append(", ");
                }
                json.append("\"").append(escape(document.lines().get(i))).append("\"");
            }
            json.append("],\n");
            json.append(indent).append("    \"document_type\": \"").append(document.documentType()).append("\",\n");
            json.append(indent).append("    \"document_number\": \"").append(escape(document.documentNumber())).append("\",\n");
            json.append(indent).append("    \"issuing_state\": \"").append(document.issuingState()).append("\",\n");
            json.append(indent).append("    \"nationality\": \"").append(document.nationality()).append("\",\n");
            json.append(indent).append("    \"surname\": \"").append(document.surname()).append("\",\n");
            json.append(indent).append("    \"given_names\": \"").append(document.givenNames()).append("\",\n");
            json.append(indent).append("    \"expired\": ").append(document.expired()).append("\n");
            json.append(indent).append("  }\n");
            json.append(indent).append("}");
            return json.toString();
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
