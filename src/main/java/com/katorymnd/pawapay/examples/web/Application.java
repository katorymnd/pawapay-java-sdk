package com.katorymnd.pawapay.examples.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Year;
import java.util.StringJoiner;

public class Application {
    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            // 1. Serve static assets (CSS, images, flags)
            // Updated to point to the new Maven resources folder
            config.staticFiles.add("src/main/resources/web/static", Location.EXTERNAL);
        }).start(8080);

        // 2. Map the routes to your specific HTML templates
        app.get("/", ctx -> renderTemplate(ctx, "home.html"));
        app.get("/deposit", ctx -> renderTemplate(ctx, "deposit_wizard.html"));
        app.get("/deposit-payment-page", ctx -> renderTemplate(ctx, "deposit_payment_page.html"));
        app.get("/withdraw", ctx -> renderTemplate(ctx, "withdraw.html"));
        app.get("/refund", ctx -> renderTemplate(ctx, "refund.html"));
        app.get("/config", ctx -> renderTemplate(ctx, "save_config.html"));

        System.out.println("\n=========================================");
        System.out.println("🚀 Pawapay SDK Web Demo is LIVE");
        System.out.println("👉 Open: http://localhost:8080");
        System.out.println("=========================================\n");
    }

    /**
     * Helper method to read the file content from the file system.
     */
    private static String readFile(String relativePath) throws Exception {
        // Updated to read from the new Maven resources folder
        return new String(Files.readAllBytes(Paths.get("src/main/resources/web/templates/" + relativePath)));
    }

    /**
     * Renders the template by assembling partials into the main page content.
     */
    private static void renderTemplate(Context ctx, String fileName) {
        StringJoiner debugCollector = new StringJoiner("\n");  // Collect debug lines here
        try {
            String html = readFile(fileName);
            debugCollector.add("DEBUG: Loaded " + fileName + " (length: " + html.length() + "). First 200 chars: " + html.substring(0, Math.min(200, html.length())));
            // Check if placeholders exist before replacement
            debugCollector.add("DEBUG: Does html contain '{{nav}}'? " + html.contains("{{nav}}"));
            debugCollector.add("DEBUG: Does html contain '{{header}}'? " + html.contains("{{header}}"));
            debugCollector.add("DEBUG: Does html contain '{{footer}}'? " + html.contains("{{footer}}"));

            String nav = readFile("partials/nav.html");
            debugCollector.add("DEBUG: Loaded nav.html (length: " + nav.length() + "). First 100 chars: " + nav.substring(0, Math.min(100, nav.length())));

            String header = readFile("partials/header.html");
            debugCollector.add("DEBUG: Loaded header.html (length: " + header.length() + "). First 100 chars: " + header.substring(0, Math.min(100, header.length())));

            String footer = readFile("partials/footer.html");
            debugCollector.add("DEBUG: Loaded footer.html (length: " + footer.length() + "). First 100 chars: " + footer.substring(0, Math.min(100, footer.length())));

            // Replace dynamic values in partials (e.g., current year)
            footer = footer.replace("{{ currentYear }}", String.valueOf(Year.now().getValue()));
            debugCollector.add("DEBUG: Footer after currentYear replace (first 100 chars): " + footer.substring(0, Math.min(100, footer.length())));

            // Inject partials into the main template
            html = html.replace("{{nav}}", nav);
            html = html.replace("{{header}}", header);
            html = html.replace("{{footer}}", footer);

            debugCollector.add("DEBUG: HTML after replacements (length: " + html.length() + "). First 200 chars: " + html.substring(0, Math.min(200, html.length())));
            // Check again after replacement
            debugCollector.add("DEBUG: After replace, does html still contain '{{nav}}'? " + html.contains("{{nav}}"));

            // Inject debug into HTML as JS console logs (at the end, before </body>)
            String debugScript = "<script>\n" +
                                 "const debugLogs = `" + debugCollector.toString().replaceAll("`", "\\`") + "`;\n" +  // Escape for JS template literal
                                 "debugLogs.split('\\n').forEach(log => console.log(log));\n" +
                                 "</script>\n";
            html = html.replace("</body>", debugScript + "</body>");

            ctx.html(html);
        } catch (Exception e) {
            debugCollector.add("DEBUG: Exception in renderTemplate: " + e.getMessage());
            // Still try to send debug to browser even on error
            String errorHtml = "<html><body><h1>Error</h1><p>" + e.getMessage() + "</p></body></html>";
            String debugScript = "<script>\n" +
                                 "const debugLogs = `" + debugCollector.toString().replaceAll("`", "\\`") + "`;\n" +
                                 "debugLogs.split('\\n').forEach(log => console.log(log));\n" +
                                 "</script>\n";
            errorHtml = errorHtml.replace("</body>", debugScript + "</body>");
            ctx.status(404).html(errorHtml);
            e.printStackTrace();  // Keep server-side stack trace
        }
    }
}