
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import com.sun.net.httpserver.*;

public class DisasterManagementSystem extends JFrame {

    // ============================================================
    // AI-POWERED DISASTER MANAGEMENT SYSTEM
    // Single-file Java 17 + Swing application
    //
    // REAL LOCATION FLOW:
    // Swing -> opens browser -> browser requests geolocation permission
    // -> browser sends latitude/longitude to this Java app
    // -> Java calls Nominatim reverse geocoding + Open-Meteo weather
    // ============================================================

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    // Shared application state
    private String username = "";
    private String mobile = "";

    private volatile double latitude = Double.NaN;
    private volatile double longitude = Double.NaN;
    private volatile String address = "Location not detected";
    private volatile String placeName = "Unknown";
    private volatile String weatherCondition = "Waiting for live data";
    private volatile double temperature = Double.NaN;
    private volatile double feelsLike = Double.NaN;
    private volatile int humidity = -1;
    private volatile double wind = Double.NaN;
    private volatile double rainfall = Double.NaN;
    private volatile int rainProbability = -1;
    private volatile String forecastText = "Waiting for live forecast";

    private String detectedDisaster = "NO ACTIVE DISASTER";
    private int riskPercentage = 0;
    private String riskLevel = "NOT ASSESSED";

    private final Color BG = new Color(5, 9, 17);
    private final Color PANEL = new Color(13, 21, 34);
    private final Color PANEL2 = new Color(18, 29, 46);
    private final Color CYAN = new Color(0, 210, 255);
    private final Color BLUE = new Color(50, 125, 255);
    private final Color RED = new Color(255, 70, 70);
    private final Color ORANGE = new Color(255, 150, 40);
    private final Color GREEN = new Color(55, 220, 130);
    private final Color WHITE = new Color(238, 244, 250);
    private final Color MUTED = new Color(155, 172, 193);

    private JLabel locationLabel;
    private JLabel coordinatesLabel;
    private JLabel addressLabel;
    private JLabel weatherLabel;
    private JLabel forecastLabel;
    private JLabel disasterContextLabel;

    // Page 9 live emergency-response state
    private JLabel page9LocationLabel;
    private JLabel page9CoordinatesLabel;
    private JLabel page9DisasterLabel;
    private JLabel page9RiskLabel;
    private JLabel page9RiskLevelLabel;
    private JTextArea page9ServicesArea;
    private JComboBox<String> page9DestinationCombo;
    private JLabel page9RouteResultLabel;
    private JTextArea page9DirectionsArea;
    private volatile EmergencyPlace selectedDestination = null;
    private JPanel page9MapView;
    private final java.util.List<EmergencyPlace> emergencyPlaces = new ArrayList<>();
    private final java.util.List<Point2DData> routePoints = new ArrayList<>();
    private volatile String routeStatus = "No route calculated";

    private HttpServer locationServer;
    private final ExecutorService background = Executors.newCachedThreadPool();

    public DisasterManagementSystem() {
        setTitle("AI-POWERED DISASTER MANAGEMENT SYSTEM");
        setSize(1450, 900);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                shutdown();
                dispose();
            }
        });

        root.setBackground(BG);

        root.add(page1Welcome(), "P1");
        root.add(page2Login(), "P2");
        root.add(page3LocationWeather(), "P3");
        root.add(page4Scan(), "P4");
        root.add(page5Safety(), "P5");
        root.add(page6Assistant(), "P6");
        root.add(page7Contacts(), "P7");
        root.add(page8DigitalTwin(), "P8");
        root.add(page9Emergency(), "P9");

        setContentPane(root);
        cards.show(root, "P1");

        startLocationServer();
    }

    // ============================================================
    // THEME HELPERS
    // ============================================================

    private JPanel pageShell() {
        JPanel p = new JPanel(new BorderLayout(18, 18));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(24, 32, 24, 32));
        return p;
    }

    private JLabel title(String s, int size) {
        JLabel l = new JLabel(s);
        l.setForeground(WHITE);
        l.setFont(new Font("Segoe UI", Font.BOLD, size));
        return l;
    }

    private JLabel subtitle(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(MUTED);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        return l;
    }

    private JPanel card(String heading) {
        JPanel p = new JPanel(new BorderLayout(12, 12));
        p.setBackground(PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(39, 62, 88), 1),
                new EmptyBorder(18, 18, 18, 18)
        ));
        JLabel h = title(heading, 18);
        h.setForeground(CYAN);
        p.add(h, BorderLayout.NORTH);
        return p;
    }

    private JButton button(String text) {
        JButton b = new JButton(text);
        b.setForeground(WHITE);
        b.setBackground(new Color(20, 42, 66));
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setUI(new BasicButtonUI());
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setRolloverEnabled(false);
        b.setMinimumSize(new Dimension(150, 42));
        b.setPreferredSize(new Dimension(180, 44));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CYAN, 1),
                new EmptyBorder(11, 18, 11, 18)
        ));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                b.setBackground(new Color(0, 88, 120));
            }
            @Override public void mouseExited(MouseEvent e) {
                b.setBackground(new Color(20, 42, 66));
            }
        });
        return b;
    }

    private JButton nextButton(String page) {
        JButton b = button("NEXT  →");
        // Bright filled colour so the navigation button is easy to see.
        b.setBackground(new Color(0, 150, 190));
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 230, 255), 2),
                new EmptyBorder(10, 22, 10, 22)
        ));
        b.addActionListener(e -> {
            cards.show(root, page);
            if ("P9".equals(page)) {
                updatePage9Context();
                if (!Double.isNaN(latitude) && !Double.isNaN(longitude) && emergencyPlaces.isEmpty()) {
                    loadNearbyEmergencyServices();
                }
            }
        });
        return b;
    }

    private JButton backButton(String page) {
        JButton b = button("←  BACK");
        // Bright orange/red navigation colour; never use a white-on-white look.
        b.setBackground(new Color(180, 75, 45));
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 165, 90), 2),
                new EmptyBorder(10, 22, 10, 22)
        ));
        b.addActionListener(e -> cards.show(root, page));
        return b;
    }

    private JPanel navigationBar(String backPage, String nextPage) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.add(backButton(backPage), BorderLayout.WEST);
        p.add(nextButton(nextPage), BorderLayout.EAST);
        return p;
    }

    private JButton quickButton(String text) {
        JButton b = new JButton(text);
        b.setUI(new BasicButtonUI());
        b.setFont(new Font("Segoe UI Emoji", Font.BOLD, 13));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setFocusPainted(false);
        b.setRolloverEnabled(false);
        b.setPreferredSize(new Dimension(175, 48));
        b.setMinimumSize(new Dimension(150, 46));
        b.setMaximumSize(new Dimension(230, 52));
        b.setHorizontalAlignment(SwingConstants.CENTER);

        // Use clearly different filled colours instead of white text on a dark button.
        Color fill;
        Color border;
        if (text.contains("Cyclone")) {
            fill = new Color(255, 120, 70);
            border = new Color(255, 190, 120);
        } else if (text.contains("Flood")) {
            fill = new Color(35, 145, 220);
            border = new Color(110, 210, 255);
        } else if (text.contains("Tsunami")) {
            fill = new Color(25, 175, 170);
            border = new Color(110, 245, 230);
        } else if (text.contains("Volcano")) {
            fill = new Color(220, 70, 70);
            border = new Color(255, 140, 140);
        } else if (text.contains("Emergency")) {
            fill = new Color(205, 55, 75);
            border = new Color(255, 125, 145);
        } else { // Emergency Kit
            fill = new Color(150, 105, 210);
            border = new Color(205, 165, 255);
        }

        b.setBackground(fill);
        b.setForeground(new Color(8, 15, 25));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 2),
                new EmptyBorder(9, 12, 9, 12)
        ));

        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                b.setForeground(Color.WHITE);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.WHITE, 2),
                        new EmptyBorder(9, 12, 9, 12)
                ));
            }
            @Override public void mouseExited(MouseEvent e) {
                b.setForeground(new Color(8, 15, 25));
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(border, 2),
                        new EmptyBorder(9, 12, 9, 12)
                ));
            }
        });
        return b;
    }

    private JPanel bottomRight(JButton b) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.setBackground(BG);
        p.add(b);
        return p;
    }

    private JLabel valueLabel(String value) {
        JLabel l = new JLabel(value);
        l.setForeground(WHITE);
        l.setFont(new Font("Segoe UI", Font.BOLD, 16));
        return l;
    }

    // ============================================================
    // PAGE 1
    // ============================================================

    private JPanel page1Welcome() {
        JPanel page = pageShell();
        final int[] tick = {0};

        // ---------- HERO (title + animated radar) ----------
        JPanel hero = new JPanel(new BorderLayout(20, 10));
        hero.setBackground(new Color(7, 24, 38));
        hero.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CYAN, 1),
                new EmptyBorder(18, 28, 18, 28)));

        JPanel heroText = new JPanel();
        heroText.setOpaque(false);
        heroText.setLayout(new BoxLayout(heroText, BoxLayout.Y_AXIS));

        JLabel badge = title("●  AI EMERGENCY INTELLIGENCE PLATFORM", 13);
        badge.setForeground(CYAN);
        heroText.add(badge);
        heroText.add(Box.createVerticalStrut(8));
        JLabel t = title("AI-POWERED DISASTER", 38);
        t.setForeground(Color.WHITE);
        heroText.add(t);
        JLabel t2 = title("MANAGEMENT SYSTEM", 38);
        t2.setForeground(CYAN);
        heroText.add(t2);
        heroText.add(Box.createVerticalStrut(8));
        heroText.add(subtitle("Predict  •  Monitor  •  Protect  •  Respond"));
        heroText.add(Box.createVerticalStrut(12));
        JLabel info = new JLabel("Real-time location + weather  |  AI risk assessment  |  Smart emergency routing");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        info.setForeground(new Color(180, 205, 215));
        heroText.add(info);
        hero.add(heroText, BorderLayout.CENTER);

        JPanel radar = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), hh = getHeight();
                int cx = w / 2, cy = hh / 2 - 8;
                int R = Math.min(w, hh) / 2 - 18;
                x.setColor(new Color(0, 40, 55));
                x.fillOval(cx - R, cy - R, R * 2, R * 2);
                x.setColor(new Color(0, 160, 190, 150));
                for (int i = 1; i <= 3; i++) x.drawOval(cx - R * i / 3, cy - R * i / 3, R * 2 * i / 3, R * 2 * i / 3);
                x.drawLine(cx - R, cy, cx + R, cy);
                x.drawLine(cx, cy - R, cx, cy + R);
                double ang = (tick[0] * 4) % 360;
                for (int i = 0; i < 40; i++) {
                    x.setColor(new Color(0, 230, 255, Math.max(0, 110 - i * 3)));
                    x.fillArc(cx - R, cy - R, R * 2, R * 2, (int) (-(ang - i * 2)), -2);
                }
                x.setColor(new Color(120, 255, 255));
                x.setStroke(new BasicStroke(2));
                x.drawLine(cx, cy, cx + (int) (R * Math.cos(Math.toRadians(ang))), cy + (int) (R * Math.sin(Math.toRadians(ang))));
                // hazard blips light up as the sweep passes them
                double[][] blips = {{40, 0.7}, {140, 0.5}, {215, 0.8}, {300, 0.45}};
                Color[] bc = {RED, BLUE, CYAN, ORANGE};
                for (int i = 0; i < blips.length; i++) {
                    double diff = ((ang - blips[i][0]) % 360 + 360) % 360;
                    int alpha = (int) Math.max(60, 255 - diff * 1.2);
                    int bx2 = cx + (int) (R * blips[i][1] * Math.cos(Math.toRadians(blips[i][0])));
                    int by2 = cy + (int) (R * blips[i][1] * Math.sin(Math.toRadians(blips[i][0])));
                    x.setColor(new Color(bc[i].getRed(), bc[i].getGreen(), bc[i].getBlue(), alpha));
                    x.fillOval(bx2 - 5, by2 - 5, 10, 10);
                }
                x.setFont(new Font("Segoe UI", Font.BOLD, 12));
                x.setColor(new Color(80, 230, 160));
                String clock = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                x.drawString("● LIVE   " + clock, cx - 45, hh - 6);
                x.dispose();
            }
        };
        radar.setOpaque(false);
        radar.setPreferredSize(new Dimension(250, 190));
        hero.add(radar, BorderLayout.EAST);
        page.add(hero, BorderLayout.NORTH);

        // ---------- CENTER: hazard cards + module chips ----------
        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setBackground(BG);

        JLabel monitor = title("DISASTER INTELLIGENCE MONITOR", 16);
        monitor.setForeground(new Color(190, 215, 225));
        center.add(monitor, BorderLayout.NORTH);

        JPanel row = new JPanel(new GridLayout(1, 4, 14, 14));
        row.setBackground(BG);
        row.add(hazardCard("🌪", "CYCLONE", RED, "Wind-speed tracking and storm alerts"));
        row.add(hazardCard("🌊", "FLOOD", BLUE, "Rainfall and forecast risk analysis"));
        row.add(hazardCard("🌊", "TSUNAMI", CYAN, "Coastal danger guidance and routes"));
        row.add(hazardCard("🌋", "VOLCANO", ORANGE, "Evacuation and ashfall safety steps"));
        center.add(row, BorderLayout.CENTER);

        JPanel modulesPanel = new JPanel(new BorderLayout(0, 6));
        modulesPanel.setBackground(BG);
        JLabel inside = title("WHAT'S INSIDE", 13);
        inside.setForeground(MUTED);
        modulesPanel.add(inside, BorderLayout.NORTH);
        JPanel chips = new JPanel(new GridLayout(2, 5, 8, 8));
        chips.setBackground(BG);
        String[] mods = {"◎ LIVE LOCATION", "◈ WEATHER INTEL", "◆ AI SCAN", "▣ CLASSIFICATION", "⚠ EMERGENCY ALERT",
                "✚ SAFETY PLAYBOOK", "✦ AI ASSISTANT", "☎ SOS CONTACTS", "⬢ DIGITAL TWIN", "➤ SMART ROUTING"};
        for (String m : mods) {
            JLabel c = infoChip(m);
            c.setHorizontalAlignment(SwingConstants.CENTER);
            chips.add(c);
        }
        modulesPanel.add(chips, BorderLayout.CENTER);
        center.add(modulesPanel, BorderLayout.SOUTH);
        page.add(center, BorderLayout.CENTER);

        // ---------- BOTTOM: scrolling ticker + start ----------
        JPanel bottom = new JPanel(new BorderLayout(14, 0));
        bottom.setBackground(BG);
        final String tickerText = "●  SYSTEM ONLINE      ☎ NATIONAL EMERGENCY 112      ☎ DISASTER MANAGEMENT 1070      " +
                "☎ DISTRICT CONTROL ROOM 1077      ✚ KEEP YOUR EMERGENCY KIT READY      ➤ FOLLOW OFFICIAL ALERTS      ";
        JPanel ticker = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                x.setColor(new Color(8, 28, 40));
                x.fillRect(0, 0, getWidth(), getHeight());
                x.setFont(new Font("Segoe UI Emoji", Font.BOLD, 13));
                x.setColor(new Color(110, 225, 245));
                int tw = x.getFontMetrics().stringWidth(tickerText);
                int px = -((tick[0] * 3) % tw);
                for (int i = 0; px + i * tw < getWidth() + tw; i++) x.drawString(tickerText, px + i * tw, 24);
                x.dispose();
            }
        };
        ticker.setPreferredSize(new Dimension(300, 40));
        ticker.setBorder(BorderFactory.createLineBorder(new Color(45, 90, 105), 1));
        bottom.add(ticker, BorderLayout.CENTER);

        JButton start = button("GET STARTED  →");
        start.setFont(new Font("Segoe UI", Font.BOLD, 18));
        start.setPreferredSize(new Dimension(210, 48));
        start.setBackground(new Color(0, 150, 190));
        start.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 230, 255), 2), new EmptyBorder(8, 18, 8, 18)));
        start.addActionListener(e -> cards.show(root, "P2"));
        bottom.add(start, BorderLayout.EAST);
        page.add(bottom, BorderLayout.SOUTH);

        new javax.swing.Timer(40, e -> {
            tick[0]++;
            radar.repaint();
            ticker.repaint();
        }).start();

        return page;
    }

    private JPanel hazardCard(String emoji, String name, Color accent, String desc) {
        JPanel p = new JPanel();
        p.setBackground(new Color(10, 29, 43));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(4, 0, 0, 0, accent),
                new EmptyBorder(12, 10, 12, 10)));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel icon = new JLabel(emoji);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 54));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel n = title(name, 18);
        n.setForeground(accent);
        n.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel d = new JLabel("<html><center>" + desc + "</center></html>");
        d.setForeground(MUTED);
        d.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        d.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel live = new JLabel("● MONITORING");
        live.setForeground(GREEN);
        live.setFont(new Font("Segoe UI", Font.BOLD, 11));
        live.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(Box.createVerticalGlue());
        p.add(icon);
        p.add(Box.createVerticalStrut(6));
        p.add(n);
        p.add(Box.createVerticalStrut(6));
        p.add(d);
        p.add(Box.createVerticalStrut(8));
        p.add(live);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JLabel infoChip(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(new Color(175, 205, 215));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(45, 90, 105), 1),
                new EmptyBorder(7, 11, 7, 11)));
        return l;
    }

    private JPanel disasterVisual(String emoji, String name, Color accent) {
        JPanel p = new JPanel();
        p.setBackground(new Color(10, 29, 43));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent.darker(), 1),
                new EmptyBorder(12, 8, 12, 8)));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel(emoji);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 58));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel n = title(name, 18);
        n.setForeground(accent);
        n.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel small = subtitle("RISK MONITORING");
        small.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(Box.createVerticalGlue());
        p.add(icon);
        p.add(Box.createVerticalStrut(8));
        p.add(n);
        p.add(Box.createVerticalStrut(5));
        p.add(small);
        p.add(Box.createVerticalGlue());
        return p;
    }

    // ============================================================
    // PAGE 2
    // ============================================================

    private JTextField loginField() {
        JTextField f = new JTextField();
        f.setMaximumSize(new Dimension(380, 46));
        f.setPreferredSize(new Dimension(380, 46));
        f.setFont(new Font("Segoe UI", Font.PLAIN, 17));
        f.setForeground(WHITE);
        f.setBackground(new Color(8, 18, 30));
        f.setCaretColor(CYAN);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 150, 190), 1),
                new EmptyBorder(8, 12, 8, 12)));
        f.setAlignmentX(Component.CENTER_ALIGNMENT);
        return f;
    }

    private JPanel page2Login() {
        JPanel page = pageShell();
        final int[] tick = {0};

        JPanel split = new JPanel(new GridLayout(1, 2, 24, 24));
        split.setBackground(BG);

        // ---------- LEFT: animated storm / radar scene ----------
        JPanel storm = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), hh = getHeight();
                x.setPaint(new GradientPaint(0, 0, new Color(6, 12, 24), 0, hh, new Color(16, 34, 58)));
                x.fillRect(0, 0, w, hh);
                int t = tick[0];

                // radar rings expanding from the lower middle
                int rcx = w / 2, rcy = (int) (hh * 0.66);
                x.setStroke(new BasicStroke(2));
                for (int k = 0; k < 4; k++) {
                    int r = (t * 2 + k * 55) % 220;
                    x.setColor(new Color(0, 210, 255, Math.max(0, 150 - r)));
                    x.drawOval(rcx - r, rcy - r, r * 2, r * 2);
                }
                // clouds
                for (int i = 0; i < 6; i++) {
                    int cxp = (int) ((i * 170 + t * 0.6) % (w + 240)) - 120;
                    int cyp = 18 + (i % 2) * 34;
                    x.setColor(new Color(28, 38, 56, 235));
                    x.fillOval(cxp, cyp, 230, 72);
                    x.fillOval(cxp + 60, cyp - 22, 140, 70);
                }
                // rain
                x.setStroke(new BasicStroke(1));
                x.setColor(new Color(150, 200, 255, 110));
                for (int i = 0; i < 130; i++) {
                    int rx = (i * 47) % Math.max(1, w), ry = (i * 83 + t * 16) % Math.max(1, hh);
                    x.drawLine(rx, ry, rx - 3, ry + 14);
                }
                // lightning
                int phase = t % 150;
                if (phase < 7) {
                    x.setColor(new Color(255, 255, 255, 38));
                    x.fillRect(0, 0, w, hh);
                    java.util.Random lr = new java.util.Random(t / 150);
                    int bxp = (int) (w * (0.3 + 0.4 * lr.nextDouble())), byp = 60;
                    x.setColor(new Color(255, 255, 210));
                    x.setStroke(new BasicStroke(3));
                    for (int i = 0; i < 8; i++) {
                        int nx = bxp + lr.nextInt(50) - 25, ny = byp + 38;
                        x.drawLine(bxp, byp, nx, ny);
                        bxp = nx; byp = ny;
                    }
                }
                // text overlay
                x.setColor(CYAN);
                x.setFont(new Font("Segoe UI", Font.BOLD, 13));
                x.drawString("●  AI EMERGENCY INTELLIGENCE", 30, hh - 190);
                x.setColor(Color.WHITE);
                x.setFont(new Font("Segoe UI", Font.BOLD, 32));
                x.drawString("DISASTER", 30, hh - 152);
                x.drawString("COMMAND CENTER", 30, hh - 116);
                x.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
                x.setColor(new Color(190, 215, 225));
                String[] feats = {"◎  Live GPS location & weather", "◆  AI risk scan and emergency alerts",
                        "➤  Smart routing to hospitals & shelters", "☎  One-tap SOS options"};
                for (int i = 0; i < feats.length; i++) x.drawString(feats[i], 34, hh - 82 + i * 22);
                x.dispose();
            }
        };
        storm.setBorder(BorderFactory.createLineBorder(new Color(39, 62, 88), 1));
        split.add(storm);

        // ---------- RIGHT: login card ----------
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CYAN, 2),
                new EmptyBorder(26, 34, 26, 34)));
        card.setPreferredSize(new Dimension(450, 520));

        JLabel icon = new JLabel("🛡");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 54));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel t = title("SECURE ACCESS", 28);
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel s = subtitle("Sign in to open disaster intelligence");
        s.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField user = loginField();
        JTextField phone = loginField();
        // Only digits are accepted, and never more than 10 of them.
        ((javax.swing.text.AbstractDocument) phone.getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
            private boolean ok(String current, int removeLen, String add) {
                return add.matches("[0-9]*") && (current.length() - removeLen + add.length()) <= 10;
            }
            @Override public void insertString(FilterBypass fb, int off, String str, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                if (str != null && ok(fb.getDocument().getText(0, fb.getDocument().getLength()), 0, str))
                    super.insertString(fb, off, str, a);
            }
            @Override public void replace(FilterBypass fb, int off, int len, String str, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                String tx = str == null ? "" : str;
                if (ok(fb.getDocument().getText(0, fb.getDocument().getLength()), len, tx))
                    super.replace(fb, off, len, tx, a);
            }
        });

        JLabel ul = title("👤  USERNAME", 13);
        ul.setForeground(CYAN);
        ul.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel ml = title("📱  MOBILE NUMBER", 13);
        ml.setForeground(CYAN);
        ml.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel userHint = subtitle("Letters only");
        userHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        userHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel phoneHint = subtitle("0 / 10 digits");
        phoneHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        phoneHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        Runnable hints = () -> {
            String u = user.getText().trim();
            boolean uok = u.matches("[\\p{L}][\\p{L} .'_-]*");
            userHint.setText(u.isEmpty() ? "Letters only" : (uok ? "✔ Looks good" : "✖ Use letters only, starting with a letter"));
            userHint.setForeground(u.isEmpty() ? MUTED : (uok ? GREEN : ORANGE));
            int n = phone.getText().length();
            phoneHint.setText(n == 10 ? "✔ 10 / 10 digits" : n + " / 10 digits");
            phoneHint.setForeground(n == 10 ? GREEN : (n == 0 ? MUTED : ORANGE));
        };
        javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { hints.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { hints.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { hints.run(); }
        };
        user.getDocument().addDocumentListener(dl);
        phone.getDocument().addDocumentListener(dl);

        JButton enter = button("ENTER COMMAND CENTER  →");
        enter.setBackground(new Color(0, 150, 190));
        enter.setMaximumSize(new Dimension(380, 48));
        enter.setPreferredSize(new Dimension(380, 48));
        enter.setAlignmentX(Component.CENTER_ALIGNMENT);
        enter.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 230, 255), 2), new EmptyBorder(8, 18, 8, 18)));
        enter.addActionListener(e -> {
            username = user.getText().trim();
            mobile = phone.getText().trim();

            if (username.isEmpty() || mobile.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please enter username and mobile number.",
                        "Login Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!username.matches("[\\p{L}][\\p{L} .'_-]*")) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a valid username (letters only, it must start with a letter).",
                        "Invalid Username", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!mobile.matches("[0-9]{10}")) {
                JOptionPane.showMessageDialog(this,
                        "Mobile number must contain exactly 10 digits (numbers only).",
                        "Invalid Mobile Number", JOptionPane.WARNING_MESSAGE);
                return;
            }
            cards.show(root, "P3");
        });
        phone.addActionListener(e -> enter.doClick());
        user.addActionListener(e -> phone.requestFocusInWindow());

        JLabel sos = subtitle("Emergency right now?  Dial 112");
        sos.setForeground(new Color(255, 140, 140));
        sos.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sos.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(icon);
        card.add(Box.createVerticalStrut(4));
        card.add(t);
        card.add(Box.createVerticalStrut(4));
        card.add(s);
        card.add(Box.createVerticalStrut(24));
        card.add(ul);
        card.add(Box.createVerticalStrut(6));
        card.add(user);
        card.add(Box.createVerticalStrut(3));
        card.add(userHint);
        card.add(Box.createVerticalStrut(14));
        card.add(ml);
        card.add(Box.createVerticalStrut(6));
        card.add(phone);
        card.add(Box.createVerticalStrut(3));
        card.add(phoneHint);
        card.add(Box.createVerticalStrut(22));
        card.add(enter);
        card.add(Box.createVerticalStrut(16));
        card.add(sos);

        JPanel right = new JPanel(new GridBagLayout());
        right.setBackground(BG);
        right.add(card);
        split.add(right);

        page.add(split, BorderLayout.CENTER);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT));
        nav.setBackground(BG);
        nav.add(backButton("P1"));
        page.add(nav, BorderLayout.SOUTH);

        new javax.swing.Timer(40, e -> { tick[0]++; storm.repaint(); }).start();
        return page;
    }

    // ============================================================
    // PAGE 3 - REAL LOCATION + REAL WEATHER
    // ============================================================

    private JPanel page3LocationWeather() {
        JPanel page = pageShell();

        JPanel h = new JPanel();
        h.setBackground(BG);
        h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
        h.add(title("LOCATION & WEATHER", 30));
        h.add(subtitle("AI-Powered Location, Weather & Disaster Monitoring"));
        page.add(h, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 18, 18));
        center.setBackground(BG);

        // LOCATION CARD
        JPanel loc = card("CURRENT LOCATION");

        JPanel locBody = new JPanel();
        locBody.setBackground(PANEL);
        locBody.setLayout(new BoxLayout(locBody, BoxLayout.Y_AXIS));

        locationLabel = valueLabel("Location: Not detected");
        coordinatesLabel = valueLabel("Latitude: --   Longitude: --");
        addressLabel = valueLabel("Address: Not detected");

        locBody.add(locationLabel);
        locBody.add(Box.createVerticalStrut(15));
        locBody.add(coordinatesLabel);
        locBody.add(Box.createVerticalStrut(15));
        locBody.add(addressLabel);
        locBody.add(Box.createVerticalGlue());

        JButton gps = button("📍 GPS / CURRENT LOCATION");
        gps.addActionListener(e -> requestRealLocation());

        JButton map = button("🌐 OPEN INTERACTIVE MAP");
        map.addActionListener(e -> openBrowserMap());

        JPanel locButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        locButtons.setBackground(PANEL);
        locButtons.add(gps);
        locButtons.add(map);

        loc.add(locBody, BorderLayout.CENTER);
        loc.add(locButtons, BorderLayout.SOUTH);

        // WEATHER CARD
        JPanel weather = card("CURRENT WEATHER");

        JPanel weatherBody = new JPanel();
        weatherBody.setBackground(PANEL);
        weatherBody.setLayout(new BoxLayout(weatherBody, BoxLayout.Y_AXIS));

        weatherLabel = valueLabel(
                "<html>Temperature: --<br>Condition: Waiting for location<br>" +
                "Feels-like: --<br>Humidity: --<br>Wind: --<br>Rainfall: --</html>"
        );

        forecastLabel = valueLabel(
                "<html>FORECAST<br><br>Waiting for live forecast...</html>"
        );

        JButton temp = button("🌡 TEMPERATURE");
        temp.addActionListener(e -> refreshWeather());

        weatherBody.add(weatherLabel);
        weatherBody.add(Box.createVerticalStrut(22));
        weatherBody.add(forecastLabel);
        weatherBody.add(Box.createVerticalGlue());

        weather.add(weatherBody, BorderLayout.CENTER);
        weather.add(temp, BorderLayout.SOUTH);

        center.add(loc);
        center.add(weather);
        page.add(center, BorderLayout.CENTER);

        page.add(navigationBar("P2", "P4"), BorderLayout.SOUTH);

        return page;
    }

    private void requestRealLocation() {
        try {
            startLocationServer();
            String url = "http://localhost:8765/location";
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                JOptionPane.showMessageDialog(this,
                        "Desktop browser opening is not available on this system.\n" +
                        "Open http://localhost:8765/location manually.",
                        "Open Location", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not open the location page:\n" + ex.getMessage(),
                    "Location Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openBrowserMap() {
        try {
            startLocationServer();
            Desktop.getDesktop().browse(URI.create("http://localhost:8765/location"));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Open http://localhost:8765/location in Chrome/Edge.",
                    "Interactive Map", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ============================================================
    // PAGE 4
    // ============================================================

    private JLabel readingChip(String caption) {
        JLabel l = new JLabel();
        l.setOpaque(true);
        l.setBackground(PANEL2);
        l.setForeground(WHITE);
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(45, 90, 105), 1),
                new EmptyBorder(8, 12, 8, 12)));
        l.setText("<html><font color='#9bacc1' size='2'>" + caption + "</font><br><b>--</b></html>");
        return l;
    }

    private JPanel page4Scan() {
        JPanel page = pageShell();

        // ---------- header + RUN FULL SCAN ----------
        JPanel head = new JPanel(new BorderLayout(14, 0));
        head.setBackground(BG);
        JPanel h = new JPanel();
        h.setBackground(BG);
        h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
        h.add(title("AI DISASTER SCAN + CLASSIFICATION + EMERGENCY ALERT", 27));
        h.add(subtitle("Live location-based disaster status — no false cyclone detection"));
        JLabel scanStatus = new JLabel("System ready. Press RUN FULL AI SCAN.");
        scanStatus.setForeground(GREEN);
        scanStatus.setFont(new Font("Segoe UI", Font.BOLD, 13));
        h.add(Box.createVerticalStrut(4));
        h.add(scanStatus);
        head.add(h, BorderLayout.CENTER);

        JButton runScan = button("⚡ RUN FULL AI SCAN");
        runScan.setBackground(new Color(40, 200, 120));
        runScan.setForeground(new Color(5, 20, 12));
        runScan.setFont(new Font("Segoe UI Emoji", Font.BOLD, 15));
        runScan.setPreferredSize(new Dimension(230, 50));
        runScan.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(170, 255, 210), 2), new EmptyBorder(8, 14, 8, 14)));
        head.add(runScan, BorderLayout.EAST);
        page.add(head, BorderLayout.NORTH);

        // ---------- live readings strip ----------
        JPanel strip = new JPanel(new GridLayout(1, 6, 10, 10));
        strip.setBackground(BG);
        JLabel cPlace = readingChip("📍 LOCATION");
        JLabel cTemp = readingChip("🌡 TEMPERATURE");
        JLabel cWind = readingChip("💨 WIND");
        JLabel cRain = readingChip("🌧 RAINFALL NOW");
        JLabel cHum = readingChip("💧 HUMIDITY");
        JLabel cChance = readingChip("☔ RAIN CHANCE TODAY");
        for (JLabel c : new JLabel[]{cPlace, cTemp, cWind, cRain, cHum, cChance}) {
            c.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
            strip.add(c);
        }
        final Runnable refreshReadings = () -> {
            String f = "<html><font color='#9bacc1' size='2'>%s</font><br><b>%s</b></html>";
            cPlace.setText(String.format(f, "📍 LOCATION", html(placeName)));
            cTemp.setText(String.format(f, "🌡 TEMPERATURE", Double.isNaN(temperature) ? "--" : num(temperature) + " °C"));
            cWind.setText(String.format(f, "💨 WIND", Double.isNaN(wind) ? "--" : num(wind) + " km/h"));
            cRain.setText(String.format(f, "🌧 RAINFALL NOW", Double.isNaN(rainfall) ? "--" : num(rainfall) + " mm"));
            cHum.setText(String.format(f, "💧 HUMIDITY", humidity < 0 ? "--" : humidity + " %"));
            cChance.setText(String.format(f, "☔ RAIN CHANCE TODAY", rainProbability < 0 ? "--" : rainProbability + " %"));
        };
        refreshReadings.run();

        // ---------- the three result cards (same results as before) ----------
        JPanel modules = new JPanel(new GridLayout(1, 3, 16, 16));
        modules.setBackground(BG);

        JPanel scan = card("AI DISASTER DETECTOR");
        JLabel scanResult = title("READY TO CHECK", 21);
        scanResult.setHorizontalAlignment(SwingConstants.CENTER);
        JButton scanButton = button("🔍 CHECK STATUS");
        scan.add(scanResult, BorderLayout.CENTER);
        scan.add(scanButton, BorderLayout.SOUTH);

        JPanel classify = card("DISASTER CLASSIFICATION");
        JLabel result = title("NOT CHECKED", 20);
        result.setHorizontalAlignment(SwingConstants.CENTER);
        JButton classifyButton = button("CLASSIFY STATUS");
        classify.add(result, BorderLayout.CENTER);
        classify.add(classifyButton, BorderLayout.SOUTH);

        JPanel alert = card("EMERGENCY ALERT");
        JLabel warning = title("NO ACTIVE ALERT", 18);
        warning.setHorizontalAlignment(SwingConstants.CENTER);
        JButton alertButton = button("🚨 CHECK EMERGENCY STATUS");
        alert.add(warning, BorderLayout.CENTER);
        alert.add(alertButton, BorderLayout.SOUTH);

        modules.add(scan);
        modules.add(classify);
        modules.add(alert);

        // ---------- bottom row: risk gauge | threat indicators | recommended actions ----------
        final double[] gaugeCur = {0};
        final double[] gaugeTarget = {0};

        JPanel gaugeCard = card("RISK METER");
        JPanel gauge = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), hh = getHeight();
                int r = Math.min(w / 2 - 24, hh - 56);
                int cx = w / 2, cy = hh - 36;
                int[][] seg = {{0, 30}, {30, 60}, {60, 80}, {80, 100}};
                Color[] sc = {GREEN, new Color(255, 215, 70), ORANGE, RED};
                x.setStroke(new BasicStroke(16, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < seg.length; i++) {
                    x.setColor(sc[i]);
                    x.drawArc(cx - r, cy - r, r * 2, r * 2, 180 - (int) (seg[i][0] * 1.8), -(int) ((seg[i][1] - seg[i][0]) * 1.8));
                }
                double ang = Math.toRadians(180 - gaugeCur[0] * 1.8);
                x.setColor(Color.WHITE);
                x.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                x.drawLine(cx, cy, cx + (int) ((r - 14) * Math.cos(ang)), cy - (int) ((r - 14) * Math.sin(ang)));
                x.fillOval(cx - 8, cy - 8, 16, 16);
                x.setFont(new Font("Segoe UI", Font.BOLD, 20));
                String txt = "RISK " + (int) Math.round(gaugeCur[0]) + "%";
                x.drawString(txt, cx - x.getFontMetrics().stringWidth(txt) / 2, cy + 28);
                x.dispose();
            }
        };
        gauge.setBackground(PANEL);
        gaugeCard.add(gauge, BorderLayout.CENTER);

        JPanel threatCard = card("LIVE THREAT INDICATORS");
        JPanel threats = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                double cyc = Double.isNaN(wind) ? -1 : Math.min(100, wind / 60.0 * 100);
                double fl = (Double.isNaN(rainfall) && rainProbability < 0) ? -1 :
                        Math.min(100, (Double.isNaN(rainfall) ? 0 : rainfall / 15.0 * 70) + Math.max(0, rainProbability) / 100.0 * 30);
                String[] names = {"CYCLONE", "FLOOD", "TSUNAMI", "VOLCANO"};
                double[] val = {cyc, fl, -2, -2};
                int barX = 92, barW = Math.max(60, w - barX - 110);
                for (int i = 0; i < 4; i++) {
                    int y = 10 + i * 30;
                    x.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    x.setColor(WHITE);
                    x.drawString(names[i], 4, y + 14);
                    x.setColor(new Color(8, 14, 24));
                    x.fillRoundRect(barX, y, barW, 18, 8, 8);
                    if (val[i] >= 0) {
                        Color c = val[i] < 30 ? GREEN : val[i] < 60 ? new Color(255, 215, 70) : val[i] < 80 ? ORANGE : RED;
                        x.setColor(c);
                        x.fillRoundRect(barX, y, (int) (barW * val[i] / 100.0), 18, 8, 8);
                        x.setColor(WHITE);
                        x.drawString((int) Math.round(val[i]) + "%", barX + barW + 8, y + 14);
                    } else {
                        x.setColor(MUTED);
                        x.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                        x.drawString(val[i] == -1 ? "waiting for live weather" : "no live sensor feed connected", barX + 8, y + 13);
                    }
                }
                x.dispose();
            }
        };
        threats.setBackground(PANEL);
        threatCard.add(threats, BorderLayout.CENTER);

        JPanel actionsCard = card("RECOMMENDED ACTIONS");
        JTextArea actions = new JTextArea();
        actions.setEditable(false);
        actions.setLineWrap(true);
        actions.setWrapStyleWord(true);
        actions.setBackground(new Color(8, 14, 24));
        actions.setForeground(WHITE);
        actions.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        actions.setBorder(new EmptyBorder(8, 10, 8, 10));
        actions.setText("Run a scan to get action steps for your current conditions.");
        actionsCard.add(new JScrollPane(actions), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(1, 3, 14, 14));
        bottom.setBackground(BG);
        bottom.setPreferredSize(new Dimension(100, 215));
        bottom.add(gaugeCard);
        bottom.add(threatCard);
        bottom.add(actionsCard);

        JPanel mid = new JPanel(new BorderLayout(0, 12));
        mid.setBackground(BG);
        mid.add(strip, BorderLayout.NORTH);
        mid.add(modules, BorderLayout.CENTER);
        mid.add(bottom, BorderLayout.SOUTH);
        page.add(mid, BorderLayout.CENTER);
        page.add(navigationBar("P3", "P5"), BorderLayout.SOUTH);

        // ---------- logic ----------
        final Runnable applyResults = () -> {
            detectedDisaster = chooseDisaster();
            riskPercentage = calculateRisk();
            boolean none = "NO ACTIVE DISASTER".equals(detectedDisaster);
            riskLevel = none ? "NO ACTIVE THREAT" : classifyRisk(riskPercentage);
            String locNote = Double.isNaN(latitude)
                    ? "<br><br><font color='#ff9a28'>Location not detected yet.<br>Use GPS on the Location page for live data.</font>" : "";

            String statusText = none ? "<font color='#37DC82'>NORMAL / SAFE</font>"
                    : "<font color='#ff6b6b'>" + detectedDisaster + "</font>";
            scanResult.setText("<html><center>CURRENT STATUS<br><br>" + statusText + "<br><br>" +
                    "Location: " + html(placeName) + locNote + "</center></html>");

            result.setText("<html><center>" + (none ? "NORMAL / SAFE" : detectedDisaster) + "<br><br>" +
                    riskLevel + "<br><br>Location: " + html(placeName) + "</center></html>");

            if (none) {
                warning.setText("<html><center>✓ NORMAL CONDITION<br><br>No active disaster detected near " +
                        html(placeName) + ".<br><br>Continue monitoring official local alerts.</center></html>");
                actions.setText("✔ No action needed right now.\n• Keep official weather alerts switched on.\n" +
                        "• Check that your emergency kit is ready.\n• Share your evacuation plan with family.\n" +
                        "• Save 112 and 1070 on your phone.");
            } else if ("CYCLONE".equals(detectedDisaster)) {
                warning.setText("<html><center>⚠ ACTIVE THREAT<br><br>" + detectedDisaster + "<br><br>" + riskLevel +
                        "<br><br>Follow official local emergency instructions.</center></html>");
                actions.setText("⚠ STRONG WINDS DETECTED\n• Stay indoors, away from windows.\n• Secure doors and windows.\n" +
                        "• Charge phones and power banks.\n• Keep your emergency kit and documents ready.\n" +
                        "• Move to a shelter if authorities order evacuation (see Smart Routing).");
            } else {
                warning.setText("<html><center>⚠ ACTIVE THREAT<br><br>" + detectedDisaster + "<br><br>" + riskLevel +
                        "<br><br>Follow official local emergency instructions.</center></html>");
                actions.setText("⚠ HEAVY RAINFALL DETECTED\n• Move valuables to upper floors.\n• Never walk or drive through floodwater.\n" +
                        "• Switch off power if water enters.\n• Be ready to move to higher ground.\n" +
                        "• Find the safest route on the Smart Routing page.");
            }
            gaugeTarget[0] = riskPercentage;
            refreshReadings.run();
            updateContexts();
            updatePage9Context();
        };

        final boolean[] scanning = {false};
        final String[] steps = {"Locating your position…", "Fetching live weather…", "Analysing wind speed…",
                "Analysing rainfall and forecast…", "Computing risk level…"};
        final Runnable startScan = () -> {
            if (scanning[0]) return;
            scanning[0] = true;
            final int[] i = {0};
            scanResult.setText("<html><center>SCANNING…</center></html>");
            javax.swing.Timer t = new javax.swing.Timer(450, null);
            t.addActionListener(ev -> {
                if (i[0] < steps.length) {
                    scanStatus.setForeground(CYAN);
                    scanStatus.setText("⚡ " + steps[i[0]]);
                    i[0]++;
                } else {
                    t.stop();
                    applyResults.run();
                    scanStatus.setForeground(GREEN);
                    scanStatus.setText("✔ Scan complete at " +
                            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                    scanning[0] = false;
                }
            });
            t.start();
        };
        runScan.addActionListener(e -> startScan.run());
        scanButton.addActionListener(e -> startScan.run());
        classifyButton.addActionListener(e -> startScan.run());
        alertButton.addActionListener(e -> startScan.run());

        // gauge needle animation + live readings refresh
        final int[] n = {0};
        new javax.swing.Timer(30, e -> {
            double d = gaugeTarget[0] - gaugeCur[0];
            if (Math.abs(d) > 0.4) gaugeCur[0] += Math.signum(d) * Math.min(Math.abs(d), 1.6);
            else gaugeCur[0] = gaugeTarget[0];
            gauge.repaint();
            if (++n[0] % 33 == 0) { refreshReadings.run(); threats.repaint(); }
        }).start();

        return page;
    }

    private int calculateRisk() {
        String disaster = chooseDisaster();
        if ("NO ACTIVE DISASTER".equals(disaster)) return 0;

        int r = 0;
        if ("CYCLONE".equals(disaster) && !Double.isNaN(wind)) {
            r = Math.max(r, Math.min(100, (int) Math.round((wind - 60) * 1.2) + 45));
        }
        if ("FLOOD".equals(disaster) && !Double.isNaN(rainfall)) {
            r = Math.max(r, Math.min(100, (int) Math.round((rainfall - 15) * 2.5) + 45));
        }
        if (humidity >= 80) r += 5;
        return Math.min(100, Math.max(1, r));
    }

    private String chooseDisaster() {
        // Never invent a disaster. Use the current live weather values only.
        // Normal conditions are explicitly reported as NORMAL / NO ACTIVE DISASTER.
        if (!Double.isNaN(wind) && wind >= 60) return "CYCLONE";
        if (!Double.isNaN(rainfall) && rainfall >= 15) return "FLOOD";
        return "NO ACTIVE DISASTER";
    }

    private String classifyRisk(int r) {
        if (r <= 30) return "LOW / SAFE";
        if (r <= 60) return "MODERATE";
        if (r <= 80) return "HIGH / SEVERE";
        return "CRITICAL / EXTREME";
    }

    // ============================================================
    // PAGE 5
    // ============================================================

    private JComboBox<String> safetyCombo;

    private java.util.Map<String, String[][]> safetyPlaybook() {
        java.util.Map<String, String[][]> m = new LinkedHashMap<>();
        m.put("Cyclone", new String[][]{
            {"Know your nearest cyclone shelter and evacuation route", "Keep a kit: water, food, medicines, torch, power bank", "Store documents in a waterproof bag", "Trim loose branches; secure roof and shutters", "Fill vehicle fuel and charge all phones"},
            {"Stay indoors, away from windows and glass", "Shelter in the strongest inner room", "Switch off electricity and gas if water enters", "Listen to official radio / alerts only", "Do not go out in the calm 'eye' — winds return"},
            {"Wait for the official all-clear before leaving", "Avoid fallen power lines and flooded roads", "Drink only boiled or bottled water", "Photograph damage for insurance claims", "Check on elderly neighbours"}});
        m.put("Flood", new String[][]{
            {"Learn which streets and areas flood first", "Keep valuables and documents on upper floors", "Prepare a go-bag with medicines and torch", "Know your higher-ground shelter location", "Keep phone numbers of local control room"},
            {"Move to higher ground immediately", "Never walk or drive through moving water", "Switch off the main power supply", "Avoid touching electrical equipment when wet", "Keep children and pets close"},
            {"Return only when authorities declare it safe", "Disinfect everything touched by floodwater", "Throw away spoiled food", "Watch for snakes and sharp debris", "Get vaccinated / treated for cuts and infections"}});
        m.put("Tsunami", new String[][]{
            {"Know if you live in a coastal danger zone", "Plan a route to ground 30 m / 2 km inland", "Practise the evacuation with family", "Keep a go-bag near the door", "Learn the natural warnings: strong quake, sea pulls back"},
            {"After a strong coastal quake, move inland at once", "Do not wait for an official siren", "Go to high ground or upper floors of strong buildings", "Stay away from the beach — never go to watch the waves", "Remember: the first wave is not the last"},
            {"Stay away until officials say it is safe (can take hours)", "Expect aftershocks", "Avoid damaged buildings and debris", "Use phone only for emergencies", "Help the injured only if it is safe"}});
        m.put("Volcano", new String[][]{
            {"Know the volcano alert levels and exclusion zone", "Keep N95 masks and goggles at home", "Plan two evacuation routes", "Keep vehicle fuel above half", "Store sealed water and food"},
            {"Follow evacuation orders immediately", "Wear mask, goggles and long sleeves in ashfall", "Close windows, doors and vents", "Avoid river valleys (lava / mudflows)", "Do not drive in heavy ashfall"},
            {"Do not return until officials allow", "Clear ash from roofs carefully — it is heavy", "Keep ash out of water supplies", "Protect machinery and engines from ash", "Wet the ash before sweeping to reduce dust"}});
        m.put("Earthquake", new String[][]{
            {"Fix tall furniture and heavy items to walls", "Identify safe spots: under a sturdy table, away from glass", "Keep torch and shoes beside the bed", "Know how to shut off gas, water and electricity", "Practise DROP – COVER – HOLD ON"},
            {"DROP to the ground, take COVER, HOLD ON", "Stay away from windows and heavy objects", "If outdoors, move to an open area", "Do not use lifts", "If driving, stop away from bridges and wires"},
            {"Expect aftershocks — be ready to cover again", "Check for gas leaks and fire hazards", "Check yourself and others for injuries", "Leave damaged buildings carefully", "Use text messages instead of calls"}});
        m.put("Landslide", new String[][]{
            {"Learn if your area is landslide-prone", "Watch for cracks, tilted trees or poles", "Keep drainage channels clear", "Plan a route away from slopes", "Keep a go-bag ready in the rainy season"},
            {"Move away from the path of the slide at once", "Listen for rumbling or cracking sounds", "Run to the nearest high, stable ground", "Avoid river channels", "Warn neighbours quickly"},
            {"Stay away from the slide area — more may follow", "Report broken utilities and blocked roads", "Check for trapped people and call rescue", "Watch for flooding after a slide", "Get the slope inspected before returning"}});
        m.put("Forest Fire", new String[][]{
            {"Clear dry leaves and brush around your home", "Keep a hose or water buckets ready", "Plan two exit routes", "Keep N95 masks and a go-bag ready", "Follow local fire-risk warnings"},
            {"Evacuate early when told — do not wait", "Close all windows and doors", "Wear cotton clothing and cover your face", "Drive with headlights on, windows closed", "If trapped, shelter in a cleared area or vehicle"},
            {"Return only when authorities allow", "Check for hot spots and smouldering embers", "Wear a mask while cleaning ash", "Check air quality before outdoor activity", "Document damage for claims"}});
        return m;
    }

    private JPanel page5Safety() {
        JPanel page = pageShell();

        JPanel h = new JPanel();
        h.setBackground(BG);
        h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
        h.add(title("SAFETY PRECAUTIONS", 30));
        h.add(subtitle("Disaster-Specific Safety Guidelines & Emergency Preparedness"));
        page.add(h, BorderLayout.NORTH);

        JPanel info = card("CURRENT DISASTER CONTEXT");
        disasterContextLabel = valueLabel(
                "Detected Disaster: " + detectedDisaster +
                "   |   Level: " + riskLevel
        );
        info.add(disasterContextLabel, BorderLayout.CENTER);

        // ---- Interactive safety playbook: pick a disaster -> BEFORE / DURING / AFTER checklists ----
        final java.util.Map<String, String[][]> playbook = safetyPlaybook();
        safetyCombo = new JComboBox<>(playbook.keySet().toArray(new String[0]));
        styleDarkCombo(safetyCombo);
        safetyCombo.setPreferredSize(new Dimension(260, 40));

        JProgressBar readiness = new JProgressBar(0, 100);
        readiness.setStringPainted(true);
        readiness.setFont(new Font("Segoe UI", Font.BOLD, 13));
        readiness.setForeground(GREEN);
        readiness.setBackground(new Color(8, 14, 24));
        readiness.setBorder(BorderFactory.createLineBorder(CYAN, 1));
        readiness.setPreferredSize(new Dimension(300, 28));

        JPanel columns = new JPanel(new GridLayout(1, 3, 12, 12));
        columns.setBackground(BG);

        Runnable rebuild = () -> {
            columns.removeAll();
            String[][] data = playbook.get(String.valueOf(safetyCombo.getSelectedItem()));
            String[] heads = {"①  BEFORE — PREPARE", "②  DURING — ACT", "③  AFTER — RECOVER"};
            Color[] accents = {CYAN, ORANGE, GREEN};
            final int total = data[0].length + data[1].length + data[2].length;
            final int[] done = {0};
            readiness.setValue(0);
            readiness.setString("READINESS 0%  (tick each step you have completed)");
            for (int col = 0; col < 3; col++) {
                JPanel box = new JPanel();
                box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
                box.setBackground(PANEL);
                box.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(4, 0, 0, 0, accents[col]),
                        new EmptyBorder(12, 14, 12, 14)));
                JLabel hd = title(heads[col], 16);
                hd.setForeground(accents[col]);
                box.add(hd);
                box.add(Box.createVerticalStrut(10));
                for (String item : data[col]) {
                    JCheckBox cb = new JCheckBox("<html><div style='width:230px'>" + item + "</div></html>");
                    cb.setOpaque(true);
                    cb.setBackground(PANEL);
                    cb.setForeground(WHITE);
                    cb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                    cb.setFocusPainted(false);
                    cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                    cb.addActionListener(ev -> {
                        done[0] += cb.isSelected() ? 1 : -1;
                        int pct = Math.round(done[0] * 100f / total);
                        readiness.setValue(pct);
                        readiness.setString("READINESS " + pct + "%" + (pct == 100 ? "  ✔ FULLY PREPARED" : ""));
                    });
                    box.add(cb);
                    box.add(Box.createVerticalStrut(6));
                }
                box.add(Box.createVerticalGlue());
                JScrollPane sp = new JScrollPane(box);
                sp.setBorder(BorderFactory.createLineBorder(new Color(38, 60, 85)));
                sp.getViewport().setBackground(PANEL);
                sp.getVerticalScrollBar().setUnitIncrement(14);
                columns.add(sp);
            }
            columns.revalidate();
            columns.repaint();
        };
        safetyCombo.addActionListener(ev -> rebuild.run());
        rebuild.run();

        JPanel selectorRow = new JPanel(new BorderLayout(14, 0));
        selectorRow.setBackground(PANEL);
        JLabel pick = title("SELECT DISASTER ▶", 14);
        pick.setForeground(CYAN);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setBackground(PANEL);
        left.add(pick);
        left.add(safetyCombo);
        selectorRow.add(left, BorderLayout.WEST);
        selectorRow.add(readiness, BorderLayout.EAST);
        info.add(selectorRow, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setBackground(BG);
        center.add(info, BorderLayout.NORTH);
        center.add(columns, BorderLayout.CENTER);
        page.add(center, BorderLayout.CENTER);

        page.add(navigationBar("P4", "P6"), BorderLayout.SOUTH);
        return page;
    }

    // ============================================================
    // PAGE 6
    // ============================================================

    private JPanel page6Assistant() {
        JPanel page = pageShell();

        JPanel h = new JPanel();
        h.setBackground(BG);
        h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
        h.add(title("AI DISASTER ASSISTANT", 30));
        h.add(subtitle("Your Intelligent Emergency & Disaster Safety Companion"));
        page.add(h, BorderLayout.NORTH);

        JTextArea chat = new JTextArea();
        chat.setEditable(false);
        chat.setLineWrap(true);
        chat.setWrapStyleWord(true);
        chat.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        chat.setForeground(WHITE);
        chat.setBackground(new Color(8, 14, 24));
        chat.setCaretColor(WHITE);
        chat.setBorder(new EmptyBorder(14, 14, 14, 14));
        chat.setText(
                "🤖 AI ASSISTANT ONLINE\n\n" +
                "Hello " + (username.isEmpty() ? "User" : username) + ".\n" +
                "Ask me about cyclone, flood, tsunami, volcano, weather, rain, location, emergency kits or safety.\n"
        );
        JScrollPane chatScroll = new JScrollPane(chat);
        chatScroll.setBorder(BorderFactory.createLineBorder(new Color(39, 62, 88), 1));
        chatScroll.getViewport().setBackground(new Color(8, 14, 24));
        page.add(chatScroll, BorderLayout.CENTER);

        JTextField input = new JTextField();
        input.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        input.setForeground(WHITE);
        input.setBackground(new Color(12, 25, 40));
        input.setCaretColor(WHITE);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CYAN, 1),
                new EmptyBorder(9, 12, 9, 12)));

        JButton ask = button("🤖 ASK AI");
        ask.setPreferredSize(new Dimension(150, 44));

        Runnable askAction = () -> {
            String q = input.getText().trim();
            if (q.isEmpty()) {
                chat.append("\nAI: Please type a question first. You can also use one of the quick-question buttons below.\n");
                chat.setCaretPosition(chat.getDocument().getLength());
                input.requestFocusInWindow();
                return;
            }

            input.setText("");
            chat.append("\nYOU: " + q + "\n");
            chat.append("AI: Thinking...\n");
            chat.setCaretPosition(chat.getDocument().getLength());
            input.requestFocusInWindow();
            // Answer in the background so the window never freezes while waiting for the internet.
            background.submit(() -> {
                String answer;
                try {
                    answer = smartAnswer(q);
                } catch (Exception ex) {
                    answer = "I could not process that question. Please try again.";
                }
                final String finalAnswer = answer;
                SwingUtilities.invokeLater(() -> {
                    try {
                        String text = chat.getText();
                        int idx = text.lastIndexOf("AI: Thinking...\n");
                        if (idx >= 0) chat.replaceRange("AI: " + finalAnswer + "\n", idx, idx + "AI: Thinking...\n".length());
                        else chat.append("AI: " + finalAnswer + "\n");
                    } catch (Exception ignored) {
                        chat.append("AI: " + finalAnswer + "\n");
                    }
                    chat.setCaretPosition(chat.getDocument().getLength());
                });
            });
        };
        ask.addActionListener(e -> askAction.run());
        input.addActionListener(e -> askAction.run());

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setBackground(BG);
        inputRow.add(input, BorderLayout.CENTER);
        JButton keyBtn = button("🔑 AI KEY");
        keyBtn.setPreferredSize(new Dimension(130, 44));
        keyBtn.addActionListener(e -> {
            JPasswordField pf = new JPasswordField(36);
            int ok = JOptionPane.showConfirmDialog(this,
                    new Object[]{"Paste your Anthropic API key to let the assistant answer ANY question.\n" +
                            "(Without a key it uses Wikipedia and built-in safety answers.)", pf},
                    "AI Assistant Key", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok == JOptionPane.OK_OPTION) {
                userApiKey = new String(pf.getPassword()).trim();
                chat.append("\nAI: " + (userApiKey.isEmpty() ? "Key cleared. Using Wikipedia / built-in answers.\n"
                        : "AI key saved. I can now answer any question you ask.\n"));
                chat.setCaretPosition(chat.getDocument().getLength());
            }
        });
        JPanel askButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        askButtons.setBackground(BG);
        askButtons.add(keyBtn);
        askButtons.add(ask);
        inputRow.add(askButtons, BorderLayout.EAST);

        JPanel quick = new JPanel(new GridLayout(2, 3, 8, 8));
        quick.setBackground(BG);
        quick.setBorder(new EmptyBorder(8, 0, 8, 0));
        String[] quickQuestions = {
                "🌪 Cyclone Safety", "🌊 Flood Safety", "🌊 Tsunami Safety",
                "🌋 Volcano Safety", "🧰 Emergency Kit", "🚨 Emergency Help"
        };
        for (String q : quickQuestions) {
            JButton b = quickButton(q);
            b.addActionListener(e -> { input.setText(q); askAction.run(); });
            quick.add(b);
        }

        JPanel bottomArea = new JPanel(new BorderLayout(0, 5));
        bottomArea.setBackground(BG);
        bottomArea.add(quick, BorderLayout.NORTH);
        bottomArea.add(inputRow, BorderLayout.CENTER);

        JPanel nav = navigationBar("P5", "P7");
        bottomArea.add(nav, BorderLayout.SOUTH);
        page.add(bottomArea, BorderLayout.SOUTH);
        return page;
    }

    // ------------------------------------------------------------
    // AI ASSISTANT BRAIN
    // 1) Live weather/location questions -> answered from the live data already fetched.
    // 2) Quick-question buttons -> built-in safety answers.
    // 3) Everything else -> Claude API (if ANTHROPIC_API_KEY is set), otherwise Wikipedia.
    // 4) If the internet is unavailable -> built-in safety answers.
    // ------------------------------------------------------------
    private static final String[] QUICK_QUESTIONS = {
            "🌪 Cyclone Safety", "🌊 Flood Safety", "🌊 Tsunami Safety",
            "🌋 Volcano Safety", "🧰 Emergency Kit", "🚨 Emergency Help"
    };

    private volatile String userApiKey = "";

    private String smartAnswer(String q) {
        String x = q.toLowerCase(Locale.ROOT);

        for (String quick : QUICK_QUESTIONS) {
            if (quick.equals(q)) return localSafetyAnswer(q);
        }

        boolean liveData = x.contains("rain") || x.contains("umbrella") || x.contains("mazhai") ||
                x.contains("weather") || x.contains("temperature") || x.contains("humidity") ||
                x.contains("forecast") || x.contains("wind speed") || x.contains("my location") ||
                x.contains("where am i") || x.contains("my address") || x.contains("my place");
        if (liveData) return localSafetyAnswer(q);

        String ai = askClaude(q);
        if (ai != null && !ai.isBlank()) return ai;

        String wiki = askWikipedia(q);
        if (wiki != null && !wiki.isBlank()) return wiki;

        return localSafetyAnswer(q);
    }

    private String askClaude(String question) {
        String key = (userApiKey != null && !userApiKey.isBlank()) ? userApiKey : System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) return null;
        try {
            String context = "The user is " + (username.isEmpty() ? "a user" : username) +
                    ". Detected location: " + placeName + " (" + address + "). " +
                    "Live weather: " + (Double.isNaN(temperature) ? "not available" :
                    num(temperature) + " C, " + weatherCondition + ", humidity " + humidity + "%, wind " + num(wind) + " km/h") +
                    ". Current disaster status: " + detectedDisaster + ".";
            String system = "You are the AI assistant inside a disaster management app. Answer the user's question " +
                    "clearly and briefly (under 150 words). Prioritise safety, tell them to follow official local " +
                    "authorities and call 112 in real emergencies. " + context;
            String body = "{\"model\":\"claude-sonnet-5-5\",\"max_tokens\":500,\"system\":\"" + jsonEscape(system) +
                    "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + jsonEscape(question) + "\"}]}";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .timeout(Duration.ofSeconds(60))
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return null;
            return jsonStringUnescaped(resp.body(), "text");
        } catch (Exception ex) {
            return null;
        }
    }

    private String askWikipedia(String question) {
        try {
            String cleaned = question.replaceAll("(?i)\\b(what|who|why|how|when|where|is|are|was|were|do|does|did|can|could|should|would|the|a|an|of|to|in|on|about|tell|me|explain|please)\\b", " ")
                    .replaceAll("[^\\p{L}\\p{N} ]", " ").trim().replaceAll("\\s+", " ");
            if (cleaned.isBlank()) cleaned = question;
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
            HttpRequest search = HttpRequest.newBuilder()
                    .uri(URI.create("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=1&srsearch=" +
                            URLEncoder.encode(cleaned, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "AI-Disaster-Management-College-Project/1.0").GET().build();
            String sj = client.send(search, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            String titleFound = jsonStringUnescaped(sj, "title");
            if (titleFound == null || titleFound.isBlank()) return null;

            HttpRequest sum = HttpRequest.newBuilder()
                    .uri(URI.create("https://en.wikipedia.org/api/rest_v1/page/summary/" +
                            URLEncoder.encode(titleFound.replace(' ', '_'), StandardCharsets.UTF_8).replace("+", "%20")))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "AI-Disaster-Management-College-Project/1.0").GET().build();
            String pj = client.send(sum, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            String extract = jsonStringUnescaped(pj, "extract");
            if (extract == null || extract.isBlank()) return null;
            if (extract.length() > 700) {
                int cut = extract.lastIndexOf(". ", 700);
                extract = extract.substring(0, cut > 200 ? cut + 1 : 700);
            }
            return extract + "\n(Source: Wikipedia — " + titleFound + ")";
        } catch (Exception ex) {
            return null;
        }
    }

    private String jsonEscape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        return b.toString();
    }

    // Reads a JSON string value for the key and properly decodes escape sequences.
    private String jsonStringUnescaped(String json, String key) {
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        while (i >= 0) {
            int colon = json.indexOf(':', i + pat.length());
            if (colon < 0) return null;
            int p = colon + 1;
            while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
            if (p < json.length() && json.charAt(p) == '"') {
                StringBuilder out = new StringBuilder();
                p++;
                while (p < json.length()) {
                    char c = json.charAt(p);
                    if (c == '"') return out.toString();
                    if (c == '\\' && p + 1 < json.length()) {
                        char n = json.charAt(++p);
                        switch (n) {
                            case 'n' -> out.append('\n');
                            case 't' -> out.append('\t');
                            case 'r' -> { }
                            case 'u' -> {
                                if (p + 4 < json.length()) {
                                    try { out.append((char) Integer.parseInt(json.substring(p + 1, p + 5), 16)); } catch (Exception ignored) {}
                                    p += 4;
                                }
                            }
                            default -> out.append(n);
                        }
                    } else out.append(c);
                    p++;
                }
                return out.toString();
            }
            i = json.indexOf(pat, i + pat.length());
        }
        return null;
    }

    private String localSafetyAnswer(String q) {
        String x = q.toLowerCase(Locale.ROOT);

        // Real location/weather questions use the live values already fetched
        // by the existing Open-Meteo + Nominatim location flow.
        if (x.contains("rain") || x.contains("rainfall") || x.contains("umbrella") ||
                x.contains("mazhai") || x.contains("chance of rain")) {
            if (rainProbability >= 0) {
                String chance = rainProbability + "%";
                if (rainProbability >= 60) {
                    return "Yes. There is a " + chance + " chance of rain today near " + placeName + ". " +
                           "Current condition: " + weatherCondition + ". Carry an umbrella and check the live forecast before travelling.";
                } else if (rainProbability >= 30) {
                    return "There is a moderate chance of rain today near " + placeName + " (about " + chance + "). " +
                           "Current condition: " + weatherCondition + ".";
                } else {
                    return "The chance of rain today near " + placeName + " is low (about " + chance + "). " +
                           "Current condition: " + weatherCondition + ".";
                }
            }
            if (!Double.isNaN(temperature)) {
                return "Current condition near " + placeName + ": " + weatherCondition +
                       ", rainfall right now: " + num(rainfall) + " mm, humidity " + humidity + "%. " +
                       "The rain-chance percentage could not be read, so please check the forecast box on the Location page.";
            }
            return "I do not have your location yet. Go to the Location page, press GPS / CURRENT LOCATION, " +
                   "allow location in the browser, then ask me again.";
        }

        if (x.contains("weather") || x.contains("temperature") || x.contains("climate") ||
                x.contains("hot") || x.contains("cold")) {
            if (!Double.isNaN(temperature)) {
                return "📍 " + placeName + " — Temperature: " + num(temperature) + " °C, " +
                       "Condition: " + weatherCondition + ", Feels like: " + num(feelsLike) +
                       " °C, Humidity: " + humidity + "%, Wind: " + num(wind) + " km/h.";
            }
            return "I have not received the live weather data yet. Please use GPS/CURRENT LOCATION and then press TEMPERATURE.";
        }

        if (x.contains("where") || x.contains("location") || x.contains("place") || x.contains("address")) {
            if (!Double.isNaN(latitude)) {
                return "📍 Your current detected location is " + placeName + ".\n" +
                       "Coordinates: " + formatCoord(latitude) + ", " + formatCoord(longitude) + ".\n" +
                       "Address: " + address;
            }
            return "Your current location has not been detected yet. Please use the GPS / CURRENT LOCATION button.";
        }

        if (x.contains("humidity")) {
            return humidity >= 0 ? "The current humidity near " + placeName + " is " + humidity + "%."
                                 : "Live humidity data is not available yet.";
        }

        if (x.contains("wind")) {
            return Double.isNaN(wind) ? "Live wind data is not available yet."
                    : "The current wind speed near " + placeName + " is " + num(wind) + " km/h.";
        }

        if (x.contains("forecast") || x.contains("tomorrow") || x.contains("next day")) {
            return forecastText.replace("<br>", "\n").replace("FORECAST\n\n", "").trim();
        }

        if (x.contains("flood")) return
                "Move to higher ground when necessary. Never walk or drive through floodwater. " +
                "Follow official evacuation instructions.";
        if (x.contains("cyclone")) return
                "Stay indoors and away from windows. Follow official weather warnings and evacuation instructions.";
        if (x.contains("tsunami")) return
                "Move away from coastal areas and toward higher ground after an official warning or strong coastal earthquake.";
        if (x.contains("volcano")) return
                "Follow official evacuation orders and stay away from restricted volcanic areas.";
        if (x.contains("kit")) return
                "Keep water, essential medicines, a flashlight, charged communication devices, food and important documents ready.";
        if (x.contains("emergency")) return
                "For a serious emergency, follow official local instructions and contact the appropriate emergency service.";

        if (x.contains("what should i do") || x.contains("what can i do") ||
                x.contains("enna pananum") || x.contains("enna seiyanum") ||
                x.contains("help me") || x.contains("safe")) {
            return "For an emergency, stay calm, move to a safer location if authorities advise evacuation, keep your phone charged, carry essential medicines/water, and follow official local warnings. " +
                   "If you tell me the disaster type (cyclone, flood, tsunami or volcano), I can give specific safety steps.";
        }

        if (x.contains("prepare") || x.contains("prepared") || x.contains("family")) {
            return "Prepare an emergency kit with water, food, medicines, flashlight, power bank and important documents. " +
                   "Know your nearest safe shelter and keep your family informed about the evacuation plan.";
        }

        if (x.contains("evacuate") || x.contains("evacuation")) {
            return "Follow official evacuation instructions, use the designated evacuation route, avoid flooded or damaged roads, and do not return until authorities give an all-clear.";
        }

        return "Yes, I can help. Ask me in a simple sentence about your location, weather, rain chance, cyclone, flood, tsunami, volcano, emergency kit, evacuation or disaster safety. For example: 'Is it going to rain in my place?'";
    }

    // ============================================================
    // PAGE 7
    // ============================================================

    private JPanel page7Contacts() {
        JPanel page = pageShell();

        JPanel h = new JPanel();
        h.setBackground(BG);
        h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
        h.add(title("EMERGENCY CONTACTS", 30));
        h.add(subtitle("Emergency services for your detected location"));
        page.add(h, BorderLayout.NORTH);

        JPanel info = card("EMERGENCY CONTACTS FOR YOUR LOCATION");
        info.add(valueLabel("Location: " + placeName + "    Address: " + address),
                BorderLayout.CENTER);

        JPanel grid = new JPanel(new GridLayout(3, 2, 12, 12));
        grid.setBackground(BG);

        grid.add(contact("NATIONAL EMERGENCY", "112"));
        grid.add(contact("POLICE", "100"));
        grid.add(contact("FIRE & RESCUE", "101"));
        grid.add(contact("AMBULANCE / MEDICAL", "108"));
        grid.add(contact("DISASTER MANAGEMENT", "1070"));
        grid.add(contact("DISTRICT CONTROL ROOM", "1077"));

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setBackground(BG);
        center.add(info, BorderLayout.NORTH);
        center.add(grid, BorderLayout.CENTER);

        page.add(center, BorderLayout.CENTER);
        page.add(navigationBar("P6", "P8"), BorderLayout.SOUTH);
        return page;
    }

    private JPanel contact(String name, String number) {
        JPanel p = card(name);
        p.add(valueLabel(number), BorderLayout.CENTER);

        JButton call = button("📞 CALL / ALERT");
        call.setBackground(new Color(40, 200, 120));
        call.setForeground(new Color(5, 20, 12));
        call.addActionListener(e -> showCallOptions(name, number));
        p.add(call, BorderLayout.SOUTH);
        return p;
    }

    // A desktop cannot place a cellular call by itself, so we give three working routes:
    // 1) QR code -> scan with your phone and it opens the dialer with the number filled in
    // 2) Open the system dialer (works with Windows Phone Link / FaceTime / softphone apps)
    // 3) Copy a ready-made SOS message containing your name, number and GPS location
    private void showCallOptions(String serviceName, String number) {
        String sos = "EMERGENCY! I need help. Name: " + (username.isEmpty() ? "User" : username) +
                ", Mobile: " + (mobile.isEmpty() ? "--" : mobile) +
                ". Location: " + placeName + " (" + formatCoord(latitude) + ", " + formatCoord(longitude) + "). " +
                (Double.isNaN(latitude) ? "" : "Map: https://www.openstreetmap.org/?mlat=" + formatCoord(latitude) +
                        "&mlon=" + formatCoord(longitude) + "#map=17/" + formatCoord(latitude) + "/" + formatCoord(longitude));
        Object[] options = {"📱 Show QR (call from phone)", "☎ Open Dialer", "📋 Copy SOS message", "Close"};
        int choice = JOptionPane.showOptionDialog(this,
                "Service: " + serviceName + "\nNumber: " + number +
                        "\n\nA desktop cannot place a cellular call directly. Choose how to reach help:",
                "Emergency — " + number, JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
        if (choice == 0) {
            background.submit(() -> {
                try {
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(URI.create(
                            "https://api.qrserver.com/v1/create-qr-code/?size=280x280&data=" +
                            URLEncoder.encode("tel:" + number, StandardCharsets.UTF_8)).toURL());
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            new JLabel("<html><center>Scan with your phone camera to dial <b>" + number +
                                    "</b></center></html>", new ImageIcon(img), SwingConstants.CENTER) {{
                                setVerticalTextPosition(SwingConstants.TOP);
                                setHorizontalTextPosition(SwingConstants.CENTER);
                            }},
                            "Scan to call " + number, JOptionPane.PLAIN_MESSAGE));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            "Could not create the QR code (internet needed).\nDial " + number + " on your phone.",
                            "QR unavailable", JOptionPane.WARNING_MESSAGE));
                }
            });
        } else if (choice == 1) {
            try {
                Desktop.getDesktop().browse(URI.create("tel:" + number));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "No calling app is linked to this computer.\nUse the QR option or dial " + number + " on your phone.",
                        "Dialer unavailable", JOptionPane.INFORMATION_MESSAGE);
            }
        } else if (choice == 2) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(sos), null);
            JOptionPane.showMessageDialog(this,
                    "SOS message copied. Paste it into WhatsApp / SMS / email and send it to family or helpers.\n\n" + sos,
                    "SOS copied", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ============================================================
    // PAGE 8
    // ============================================================

    // Is a building at normalised position (nx,ny) reached by the hazard at effective progress "eff"?
    private boolean twinHit(String type, double nx, double ny, double thr, double eff) {
        switch (type) {
            case "Flood": return ny > 1.0 - eff * 0.95;
            case "Cyclone": return Math.hypot(nx - 0.5, ny - 0.5) < eff * 0.75;
            case "Tsunami": return nx < eff * 1.1;
            case "Landslide": return nx > 1.0 - eff * 0.6;
            case "Forest Fire": return Math.hypot(nx - 0.25, ny - 0.7) < eff * 0.8;
            default: return thr < eff; // Earthquake
        }
    }

    private JPanel page8DigitalTwin() {
        JPanel page = pageShell();

        JPanel h = new JPanel();
        h.setBackground(BG);
        h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
        h.add(title("DIGITAL TWIN & DISASTER SIMULATION", 30));
        h.add(subtitle("Animated city model: watch the hazard spread, see who is affected, and test evacuation"));
        page.add(h, BorderLayout.NORTH);

        JPanel split = new JPanel(new GridLayout(1, 2, 18, 18));
        split.setBackground(BG);

        // ---------------- CITY MODEL (same every run) ----------------
        final int NB = 32;
        final double[] bx = new double[NB], by = new double[NB], thr = new double[NB];
        final int[] pop = new int[NB];
        java.util.Random rnd = new java.util.Random(11);
        for (int i = 0; i < NB; i++) {
            bx[i] = 0.06 + 0.88 * rnd.nextDouble();
            by[i] = 0.2 + 0.68 * rnd.nextDouble();
            if (Math.abs(bx[i] - 0.5) < 0.035) bx[i] += 0.06;
            if (Math.abs(by[i] - 0.5) < 0.035) by[i] += 0.06;
            thr[i] = rnd.nextDouble();
            pop[i] = 20 + rnd.nextInt(180);
        }

        final int[] simStep = {0};
        final boolean[] simRunning = {false};
        final boolean[] simPaused = {false};
        final boolean[] evacuating = {false};
        final int[] tick = {0};
        final int[] evacuated = {0};
        final int[] intensity = {3};
        final int[] logged = {0};
        final String[] selectedType = {"Flood"};

        JPanel twin = card("REAL-TIME DIGITAL TWIN VIEW");
        JPanel drawing = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), hh = getHeight();
                String type = selectedType[0];
                double prog = simStep[0] / 100.0;
                double eff = Math.min(1.0, prog * (0.5 + intensity[0] * 0.25));
                int t = tick[0];

                x.setColor(new Color(4, 15, 25));
                x.fillRect(0, 0, w, hh);

                // Earthquake: whole city shakes
                if (type.equals("Earthquake") && simRunning[0] && !simPaused[0]) {
                    x.translate(Math.sin(t * 1.9) * 4 * (0.5 + eff), Math.cos(t * 2.3) * 4 * (0.5 + eff));
                }

                // Grid + roads
                x.setColor(new Color(24, 52, 66));
                for (int i = 0; i < w; i += 45) x.drawLine(i, 0, i, hh);
                for (int i = 0; i < hh; i += 45) x.drawLine(0, i, w, i);
                x.setColor(new Color(85, 90, 98));
                x.fillRect(0, hh / 2 - 7, w, 14);
                x.fillRect(w / 2 - 7, 0, 14, hh);
                x.setColor(new Color(230, 200, 80));
                for (int i = 0; i < w; i += 30) x.fillRect(i, hh / 2 - 1, 14, 2);
                for (int i = 0; i < hh; i += 30) x.fillRect(w / 2 - 1, i, 2, 14);

                // Shelter (placed on the safe side for each hazard)
                double sx = type.equals("Tsunami") ? 0.88 : 0.1;
                double sy = type.equals("Flood") || type.equals("Tsunami") || type.equals("Cyclone") ? 0.1 : 0.12;
                int shx = (int) (sx * w), shy = (int) (sy * hh);

                // Buildings
                for (int i = 0; i < NB; i++) {
                    int bw = 22 + (i % 3) * 8, bh = 22 + (i % 4) * 9;
                    int px = (int) (bx[i] * w) - bw / 2, py = (int) (by[i] * hh) - bh / 2;
                    boolean hit = prog > 0 && twinHit(type, bx[i], by[i], thr[i], eff);
                    Color c = new Color(70, 84, 102);
                    if (hit) {
                        switch (type) {
                            case "Flood", "Tsunami" -> c = new Color(60, 120, 190);
                            case "Landslide" -> c = new Color(150, 105, 60);
                            case "Forest Fire" -> c = new Color(220, 90, 40);
                            default -> c = new Color(200, 60, 60);
                        }
                    }
                    x.setColor(c);
                    if (type.equals("Earthquake") && hit && eff > 0.7) {
                        // collapsed buildings are shorter and tilted
                        x.fillPolygon(new int[]{px, px + bw, px + bw - 6, px + 4}, new int[]{py + bh, py + bh, py + bh / 2, py + bh / 2 + 4}, 4);
                    } else {
                        x.fillRect(px, py, bw, bh);
                    }
                    x.setColor(new Color(255, 230, 150, hit ? 60 : 150));
                    for (int wy = py + 4; wy < py + bh - 4; wy += 8)
                        for (int wx = px + 4; wx < px + bw - 4; wx += 8) x.fillRect(wx, wy, 3, 3);
                }

                // ----- Hazard animations -----
                if (prog > 0) {
                    if (type.equals("Flood")) {
                        int top = (int) (hh * (1.0 - eff * 0.95));
                        java.awt.geom.Path2D.Double water = new java.awt.geom.Path2D.Double();
                        water.moveTo(0, hh);
                        for (int i = 0; i <= w; i += 8) water.lineTo(i, top + Math.sin((i + t * 6) * 0.05) * 5);
                        water.lineTo(w, hh);
                        water.closePath();
                        x.setColor(new Color(30, 130, 215, 120));
                        x.fill(water);
                        x.setColor(new Color(150, 210, 250, 150));
                        for (int i = 0; i < 70; i++) {
                            int rx = (i * 53) % Math.max(1, w), ry = (i * 97 + t * 14) % Math.max(1, hh);
                            x.drawLine(rx, ry, rx - 3, ry + 9);
                        }
                    } else if (type.equals("Cyclone")) {
                        int cx = w / 2, cy = hh / 2;
                        int rx = (int) (eff * 0.75 * w), ry = (int) (eff * 0.75 * hh);
                        x.setColor(new Color(255, 150, 45, 60));
                        x.fillOval(cx - rx, cy - ry, rx * 2, ry * 2);
                        x.setStroke(new BasicStroke(3));
                        double[] fr = {1.0, 0.78, 0.56, 0.36};
                        for (int k = 0; k < fr.length; k++) {
                            x.setColor(new Color(255, 215, 110, 200 - k * 30));
                            int ax = (int) (rx * fr[k]), ay = (int) (ry * fr[k]);
                            for (int arm = 0; arm < 3; arm++)
                                x.drawArc(cx - ax, cy - ay, ax * 2, ay * 2, (t * (6 + k * 3) + arm * 120) % 360, 70);
                        }
                        x.setColor(new Color(4, 15, 25));
                        x.fillOval(cx - 12, cy - 12, 24, 24);
                        x.setStroke(new BasicStroke(1));
                        x.setColor(new Color(220, 235, 245, 140));
                        for (int i = 0; i < 40; i++) {
                            int lx = ((i * 71) + t * 18) % Math.max(1, w), ly = (i * 37) % Math.max(1, hh);
                            x.drawLine(lx, ly, lx + 22, ly + 2);
                        }
                    } else if (type.equals("Tsunami")) {
                        int front = (int) (eff * 1.1 * w);
                        x.setColor(new Color(25, 120, 210, 135));
                        x.fillRect(0, 0, Math.min(front, w), hh);
                        x.setColor(new Color(235, 248, 255, 220));
                        x.setStroke(new BasicStroke(4));
                        for (int y2 = 0; y2 < hh; y2 += 26)
                            x.drawArc(front - 20 + (int) (Math.sin((y2 + t * 4) * 0.08) * 6), y2, 36, 30, 270, 180);
                        x.setStroke(new BasicStroke(1));
                    } else if (type.equals("Landslide")) {
                        int edge = (int) (w * (1.0 - eff * 0.6));
                        x.setColor(new Color(150, 105, 60, 120));
                        x.fillPolygon(new int[]{edge, w, w, edge + 40}, new int[]{0, 0, hh, hh}, 4);
                        x.setColor(new Color(190, 140, 85));
                        int span = Math.max(1, w - edge);
                        for (int i = 0; i < 22; i++) {
                            int rx = w - ((i * 61 + t * 4) % span), ry = ((i * 43) % Math.max(1, hh - 20)) + 8;
                            x.fillOval(rx, ry, 12 + i % 4 * 3, 10 + i % 3 * 3);
                        }
                    } else if (type.equals("Forest Fire")) {
                        int cx = (int) (0.25 * w), cy = (int) (0.7 * hh);
                        int rx = (int) (eff * 0.8 * w), ry = (int) (eff * 0.8 * hh);
                        x.setColor(new Color(255, 70, 30, 70));
                        x.fillOval(cx - rx, cy - ry, rx * 2, ry * 2);
                        for (int i = 0; i < 70; i++) {
                            double ang = i * 2.399, rr = Math.sqrt((i % 35) / 35.0);
                            int fx = cx + (int) (Math.cos(ang) * rx * rr), fy = cy + (int) (Math.sin(ang) * ry * rr);
                            int fh = 10 + (t + i * 5) % 16;
                            x.setColor(new Color(255, 120 + (i * 13) % 100, 30, 210));
                            x.fillOval(fx, fy - fh, 9, fh);
                            x.setColor(new Color(120, 120, 120, 70));
                            x.fillOval(fx - 4, fy - fh - 14 - (t + i) % 20, 16, 12);
                        }
                    } else { // Earthquake
                        int cx = w / 2, cy = hh / 2;
                        for (int k = 0; k < 4; k++) {
                            int r = ((t * 5 + k * 60) % 240) + 10;
                            x.setColor(new Color(255, 110, 90, Math.max(20, 200 - r)));
                            x.setStroke(new BasicStroke(3));
                            x.drawOval(cx - r, cy - r, r * 2, r * 2);
                        }
                        x.setStroke(new BasicStroke(3));
                        x.setColor(new Color(20, 10, 10));
                        int len = (int) (eff * 0.5 * w);
                        x.drawLine(cx, cy, cx - len, cy - len / 3);
                        x.drawLine(cx, cy, cx + len, cy + len / 4);
                        x.drawLine(cx, cy, cx + len / 3, cy - len / 2);
                        x.drawLine(cx, cy, cx - len / 4, cy + len / 2);
                        x.setStroke(new BasicStroke(1));
                    }
                }

                // Shelter marker
                x.setColor(new Color(40, 200, 120));
                x.fillRoundRect(shx - 22, shy - 16, 44, 32, 8, 8);
                x.setColor(new Color(5, 25, 15));
                x.setFont(new Font("Segoe UI", Font.BOLD, 14));
                x.drawString("H", shx - 4, shy + 5);
                x.setColor(new Color(160, 255, 205));
                x.setFont(new Font("Segoe UI", Font.BOLD, 10));
                x.drawString("SHELTER", shx - 22, shy + 29);

                // Evacuating people (green dots running to the shelter)
                if (evacuating[0] && prog > 0) {
                    for (int i = 0; i < NB; i++) {
                        if (!twinHit(type, bx[i], by[i], thr[i], eff)) continue;
                        for (int k = 0; k < 4; k++) {
                            double f = ((t * 0.012) + i * 0.137 + k * 0.23) % 1.0;
                            int px = (int) (bx[i] * w + (shx - bx[i] * w) * f);
                            int py = (int) (by[i] * hh + (shy - by[i] * hh) * f);
                            x.setColor(new Color(120, 255, 170));
                            x.fillOval(px, py, 6, 6);
                        }
                    }
                }

                // HUD (drawn without the shake offset)
                x.setTransform(((Graphics2D) g).getTransform());
                x.setColor(new Color(0, 0, 0, 150));
                x.fillRoundRect(10, 10, 250, 92, 10, 10);
                x.setColor(CYAN);
                x.setFont(new Font("Segoe UI", Font.BOLD, 16));
                x.drawString("DIGITAL TWIN — " + type.toUpperCase(), 20, 32);
                x.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                x.setColor(Color.WHITE);
                x.drawString("Location: " + placeName, 20, 52);
                x.drawString("Simulation: " + (simRunning[0] ? (simPaused[0] ? "PAUSED" : "RUNNING") : (simStep[0] >= 100 ? "COMPLETED" : "READY")), 20, 70);
                x.drawString("Progress: " + simStep[0] + "%   Intensity: " + intensity[0] + "/5", 20, 88);
                x.dispose();
            }
        };
        twin.add(drawing, BorderLayout.CENTER);

        // ---------------- CONTROLS ----------------
        JPanel controls = card("SIMULATION CONTROLS");
        JPanel cb = new JPanel();
        cb.setBackground(PANEL);
        cb.setLayout(new BoxLayout(cb, BoxLayout.Y_AXIS));

        JComboBox<String> type = new JComboBox<>(new String[]{
                "Flood", "Cyclone", "Tsunami", "Landslide", "Forest Fire", "Earthquake"
        });
        styleDarkCombo(type);
        type.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        type.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel intensityLabel = title("INTENSITY: 3 / 5", 14);
        intensityLabel.setForeground(ORANGE);
        JSlider slider = new JSlider(1, 5, 3);
        slider.setBackground(PANEL);
        slider.setForeground(CYAN);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.addChangeListener(e -> {
            intensity[0] = slider.getValue();
            intensityLabel.setText("INTENSITY: " + slider.getValue() + " / 5");
            drawing.repaint();
        });

        JLabel result = valueLabel("<html>Status: READY</html>");
        result.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea log = new JTextArea(6, 24);
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setBackground(new Color(8, 14, 24));
        log.setForeground(new Color(190, 235, 255));
        log.setFont(new Font("Consolas", Font.PLAIN, 12));
        log.setBorder(new EmptyBorder(6, 8, 6, 8));
        log.setText("EVENT LOG\n");
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(39, 62, 88)));

        JButton start = button("▶ START SIMULATION");
        JButton pause = button("Ⅱ PAUSE");
        JButton evac = button("🚶 START EVACUATION");
        JButton reset = button("↻ RESET");
        for (JButton b : new JButton[]{start, pause, evac, reset}) {
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        evac.setBackground(new Color(40, 200, 120));
        evac.setForeground(new Color(5, 20, 12));

        final Runnable refreshStats = () -> {
            String d = selectedType[0];
            double eff = Math.min(1.0, (simStep[0] / 100.0) * (0.5 + intensity[0] * 0.25));
            int buildings = 0, popAff = 0;
            for (int i = 0; i < NB; i++) {
                if (simStep[0] > 0 && twinHit(d, bx[i], by[i], thr[i], eff)) { buildings++; popAff += pop[i]; }
            }
            if (evacuating[0]) evacuated[0] = Math.min(popAff, evacuated[0] + 14 + intensity[0] * 5);
            int atRisk = Math.max(0, popAff - evacuated[0]);
            int roads = (int) Math.round(eff * 14);
            int area = (int) Math.round(eff * 120 * (0.6 + intensity[0] * 0.2));
            String threat = eff < 0.25 ? "<font color='#37dc82'>LOW</font>"
                    : eff < 0.55 ? "<font color='#ffd24a'>MODERATE</font>"
                    : eff < 0.8 ? "<font color='#ff9a28'>HIGH</font>" : "<font color='#ff5050'>CRITICAL</font>";
            String advice = eff < 0.25 ? "MONITOR" : eff < 0.55 ? "PREPARE RESPONSE" : eff < 0.8 ? "ISSUE EVACUATION ORDER" : "EMERGENCY EVACUATION";
            result.setText("<html>Status: " + (simStep[0] >= 100 ? "COMPLETED" : simRunning[0] ? (simPaused[0] ? "PAUSED" : "RUNNING") : "READY") +
                    "<br>Threat level: " + threat +
                    "<br>Progress: " + simStep[0] + "%" +
                    "<br>Affected area: " + area + " hectares" +
                    "<br>Affected buildings: " + buildings + " / " + NB +
                    "<br>Affected roads: " + roads +
                    "<br>People affected: " + popAff +
                    "<br>People evacuated: <font color='#78ffaa'>" + evacuated[0] + "</font>" +
                    "<br>People still at risk: <font color='#ff7a7a'>" + atRisk + "</font>" +
                    "<br>Decision support: " + advice + "</html>");
        };
        final String[] milestoneText = {
                "Sensors detect %s activity. Monitoring started.",
                "Early warning sent to affected wards.",
                "Roads closing, rescue teams on standby.",
                "Evacuation order recommended for the red zone.",
                "Peak impact reached. Begin damage assessment."
        };
        final int[] milestoneAt = {1, 25, 50, 75, 100};

        javax.swing.Timer timer = new javax.swing.Timer(40, e -> {
            tick[0]++;
            if (simRunning[0] && !simPaused[0] && tick[0] % 3 == 0) {
                simStep[0] = Math.min(100, simStep[0] + 1);
                while (logged[0] < milestoneAt.length && simStep[0] >= milestoneAt[logged[0]]) {
                    log.append(String.format("[%3d%%] ", milestoneAt[logged[0]]) +
                            String.format(milestoneText[logged[0]], selectedType[0]) + "\n");
                    logged[0]++;
                }
                refreshStats.run();
                if (simStep[0] >= 100) {
                    simRunning[0] = false;
                    ((javax.swing.Timer) e.getSource()).stop();
                    refreshStats.run();
                }
            }
            drawing.repaint();
        });

        type.addActionListener(e -> {
            selectedType[0] = String.valueOf(type.getSelectedItem());
            timer.stop();
            simRunning[0] = false; simPaused[0] = false; simStep[0] = 0;
            evacuating[0] = false; evacuated[0] = 0; logged[0] = 0;
            evac.setText("🚶 START EVACUATION");
            log.setText("EVENT LOG\nScenario selected: " + selectedType[0] + "\n");
            refreshStats.run();
            drawing.repaint();
        });

        start.addActionListener(e -> {
            selectedType[0] = String.valueOf(type.getSelectedItem());
            if (simStep[0] >= 100) { simStep[0] = 0; logged[0] = 0; evacuated[0] = 0; log.setText("EVENT LOG\n"); }
            simRunning[0] = true;
            simPaused[0] = false;
            timer.start();
            refreshStats.run();
        });

        pause.addActionListener(e -> {
            if (simRunning[0]) {
                simPaused[0] = !simPaused[0];
                pause.setText(simPaused[0] ? "▶ RESUME" : "Ⅱ PAUSE");
                refreshStats.run();
            }
        });

        evac.addActionListener(e -> {
            evacuating[0] = !evacuating[0];
            evac.setText(evacuating[0] ? "■ STOP EVACUATION" : "🚶 START EVACUATION");
            log.append(evacuating[0] ? "[ACT] Evacuation started — residents moving to shelter.\n"
                                     : "[ACT] Evacuation paused.\n");
            if (!timer.isRunning()) timer.start();
        });

        reset.addActionListener(e -> {
            timer.stop();
            simRunning[0] = false; simPaused[0] = false; simStep[0] = 0;
            evacuating[0] = false; evacuated[0] = 0; logged[0] = 0;
            pause.setText("Ⅱ PAUSE");
            evac.setText("🚶 START EVACUATION");
            log.setText("EVENT LOG\nSimulation reset.\n");
            refreshStats.run();
            drawing.repaint();
        });

        JLabel typeLabel = title("▼ DISASTER TYPE", 14);
        typeLabel.setForeground(CYAN);
        cb.add(typeLabel);
        cb.add(Box.createVerticalStrut(6));
        cb.add(type);
        cb.add(Box.createVerticalStrut(10));
        cb.add(intensityLabel);
        cb.add(slider);
        cb.add(Box.createVerticalStrut(8));
        cb.add(start);
        cb.add(Box.createVerticalStrut(6));
        cb.add(pause);
        cb.add(Box.createVerticalStrut(6));
        cb.add(evac);
        cb.add(Box.createVerticalStrut(6));
        cb.add(reset);
        cb.add(Box.createVerticalStrut(12));
        cb.add(result);
        cb.add(Box.createVerticalStrut(10));
        cb.add(logScroll);
        cb.add(Box.createVerticalStrut(8));
        JLabel note = subtitle("Simulation only — for planning and training. Not a live alert.");
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.add(note);
        refreshStats.run();

        JScrollPane cbScroll = new JScrollPane(cb);
        cbScroll.setBorder(null);
        cbScroll.getViewport().setBackground(PANEL);
        cbScroll.getVerticalScrollBar().setUnitIncrement(16);
        controls.add(cbScroll);
        split.add(controls);
        split.add(twin);
        page.add(split, BorderLayout.CENTER);
        page.add(navigationBar("P7", "P9"), BorderLayout.SOUTH);
        return page;
    }

    // ============================================================
    // PAGE 9
    // ============================================================

    private JPanel page9Emergency() {
        JPanel page = pageShell();

        JPanel h = new JPanel();
        h.setBackground(BG);
        h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
        h.add(title("EMERGENCY RESPONSE & SMART ROUTING", 30));
        h.add(subtitle("Real nearby emergency services + live road routing"));
        page.add(h, BorderLayout.NORTH);

        JPanel split = new JPanel(new GridLayout(1, 2, 18, 18));
        split.setBackground(BG);

        JPanel map = card("INTERACTIVE EMERGENCY MAP");
        page9MapView = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D x = (Graphics2D) g.create();
                x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                x.setColor(new Color(7, 22, 32));
                x.fillRect(0, 0, getWidth(), getHeight());

                x.setColor(new Color(35, 62, 78));
                for (int i = 0; i < getWidth(); i += 42) x.drawLine(i, 0, i, getHeight());
                for (int i = 0; i < getHeight(); i += 42) x.drawLine(0, i, getWidth(), i);

                if (!Double.isNaN(latitude) && !Double.isNaN(longitude)) {
                    double minLat = latitude, maxLat = latitude, minLon = longitude, maxLon = longitude;
                    for (EmergencyPlace ep : emergencyPlaces) {
                        minLat = Math.min(minLat, ep.lat); maxLat = Math.max(maxLat, ep.lat);
                        minLon = Math.min(minLon, ep.lon); maxLon = Math.max(maxLon, ep.lon);
                    }
                    for (Point2DData pt : routePoints) {
                        minLat = Math.min(minLat, pt.lat); maxLat = Math.max(maxLat, pt.lat);
                        minLon = Math.min(minLon, pt.lon); maxLon = Math.max(maxLon, pt.lon);
                    }
                    double latPad = Math.max(0.005, (maxLat - minLat) * 0.15);
                    double lonPad = Math.max(0.005, (maxLon - minLon) * 0.15);
                    minLat -= latPad; maxLat += latPad; minLon -= lonPad; maxLon += lonPad;
                    final double fMinLat = minLat, fMaxLat = maxLat, fMinLon = minLon, fMaxLon = maxLon;

                    java.util.function.Function<Point2DData, Point> project = pt -> {
                        int px = (int) ((pt.lon - fMinLon) / Math.max(1e-9, fMaxLon - fMinLon) * (getWidth() - 70)) + 35;
                        int py = getHeight() - ((int) ((pt.lat - fMinLat) / Math.max(1e-9, fMaxLat - fMinLat) * (getHeight() - 70)) + 35);
                        return new Point(px, py);
                    };

                    if (routePoints.size() > 1) {
                        // Glow underneath + bright route line on top, so the direction is clearly visible.
                        for (int pass = 0; pass < 2; pass++) {
                            x.setColor(pass == 0 ? new Color(55, 220, 130, 70) : GREEN);
                            x.setStroke(new BasicStroke(pass == 0 ? 14 : 6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            Point prev = null;
                            for (Point2DData pt : routePoints) {
                                Point cur = project.apply(pt);
                                if (prev != null) x.drawLine(prev.x, prev.y, cur.x, cur.y);
                                prev = cur;
                            }
                        }
                        // Direction arrows along the route
                        x.setColor(Color.WHITE);
                        x.setStroke(new BasicStroke(2));
                        int stepArrow = Math.max(1, routePoints.size() / 8);
                        for (int i = stepArrow; i < routePoints.size() - 1; i += stepArrow) {
                            Point a = project.apply(routePoints.get(i - 1));
                            Point b = project.apply(routePoints.get(i));
                            double ang = Math.atan2(b.y - a.y, b.x - a.x);
                            int ax1 = (int) (b.x - 10 * Math.cos(ang - 0.5)), ay1 = (int) (b.y - 10 * Math.sin(ang - 0.5));
                            int ax2 = (int) (b.x - 10 * Math.cos(ang + 0.5)), ay2 = (int) (b.y - 10 * Math.sin(ang + 0.5));
                            x.drawLine(b.x, b.y, ax1, ay1);
                            x.drawLine(b.x, b.y, ax2, ay2);
                        }
                    }
                    EmergencyPlace sel = selectedDestination;
                    if (sel != null && routePoints.size() > 1) {
                        Point d = project.apply(new Point2DData(sel.lat, sel.lon));
                        x.setColor(Color.WHITE);
                        x.setStroke(new BasicStroke(3));
                        x.drawOval(d.x - 13, d.y - 13, 26, 26);
                        x.setFont(new Font("Segoe UI", Font.BOLD, 12));
                        x.drawString("DESTINATION: " + sel.name, Math.max(5, d.x - 40), d.y - 18);
                    }

                    for (EmergencyPlace ep : emergencyPlaces) {
                        Point q = project.apply(new Point2DData(ep.lat, ep.lon));
                        x.setColor(serviceColor(ep.type));
                        x.fillOval(q.x - 6, q.y - 6, 12, 12);
                        x.setColor(WHITE);
                        x.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                        x.drawString(ep.type, q.x + 8, q.y + 4);
                    }

                    Point me = project.apply(new Point2DData(latitude, longitude));
                    x.setColor(CYAN);
                    x.fillOval(me.x - 8, me.y - 8, 16, 16);
                    x.setColor(WHITE);
                    x.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    x.drawString("YOU", me.x + 10, me.y + 4);
                } else {
                    x.setColor(MUTED);
                    x.setFont(new Font("Segoe UI", Font.BOLD, 16));
                    x.drawString("Waiting for current GPS location...", 35, 45);
                }
                x.dispose();
            }
        };
        page9MapView.setPreferredSize(new Dimension(600, 560));
        map.add(page9MapView, BorderLayout.CENTER);

        // Map legend: explains exactly what every coloured dot means.
        JPanel legend = new JPanel(new GridLayout(2, 3, 8, 8));
        legend.setBackground(PANEL2);
        legend.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(55, 82, 108), 1),
                new EmptyBorder(10, 10, 10, 10)
        ));
        legend.add(legendItem(RED, "HOSPITAL"));
        legend.add(legendItem(BLUE, "POLICE"));
        legend.add(legendItem(ORANGE, "FIRE STATION"));
        legend.add(legendItem(GREEN, "SHELTER"));
        legend.add(legendItem(CYAN, "YOU / LOCATION"));
        legend.add(legendItem(GREEN, "SAFE ROUTE"));

        JPanel mapWithLegend = new JPanel(new BorderLayout(8, 8));
        mapWithLegend.setBackground(PANEL);
        mapWithLegend.add(page9MapView, BorderLayout.CENTER);
        mapWithLegend.add(legend, BorderLayout.SOUTH);
        map.remove(page9MapView);
        map.add(mapWithLegend, BorderLayout.CENTER);

        JPanel response = card("EMERGENCY RESPONSE");
        JPanel rb = new JPanel();
        rb.setBackground(PANEL);
        rb.setLayout(new BoxLayout(rb, BoxLayout.Y_AXIS));

        page9LocationLabel = valueLabel("LOCATION: " + placeName);
        page9CoordinatesLabel = valueLabel("LAT/LON: " + formatCoord(latitude) + ", " + formatCoord(longitude));
        page9DisasterLabel = valueLabel("DISASTER: " + detectedDisaster);
        page9RiskLabel = valueLabel("STATUS: " + ("NO ACTIVE DISASTER".equals(detectedDisaster) ? "NORMAL" : "MONITOR"));
        page9RiskLevelLabel = valueLabel("RISK LEVEL: " + riskLevel);

        rb.add(page9LocationLabel);
        rb.add(Box.createVerticalStrut(8));
        rb.add(page9CoordinatesLabel);
        rb.add(Box.createVerticalStrut(8));
        rb.add(page9DisasterLabel);
        rb.add(Box.createVerticalStrut(8));
        rb.add(page9RiskLabel);
        rb.add(Box.createVerticalStrut(8));
        rb.add(page9RiskLevelLabel);
        rb.add(Box.createVerticalStrut(16));

        JButton emergency = button("🚨 EMERGENCY RESPONSE");
        emergency.addActionListener(e -> loadNearbyEmergencyServices());

        JButton liveMap = button("🌐 OPEN LIVE EMERGENCY MAP");
        liveMap.addActionListener(e -> openEmergencyBrowserMap());

        page9ServicesArea = new JTextArea(7, 28);
        page9ServicesArea.setEditable(false);
        page9ServicesArea.setLineWrap(true);
        page9ServicesArea.setWrapStyleWord(true);
        page9ServicesArea.setBackground(PANEL2);
        page9ServicesArea.setForeground(WHITE);
        page9ServicesArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        page9ServicesArea.setText("Press EMERGENCY RESPONSE to find nearby verified OpenStreetMap services.");
        rb.add(new JScrollPane(page9ServicesArea));
        rb.add(Box.createVerticalStrut(10));
        rb.add(emergency);
        rb.add(Box.createVerticalStrut(8));
        rb.add(liveMap);
        rb.add(Box.createVerticalStrut(12));

        JLabel destLabel = title("▼ SELECT DESTINATION", 15);
        destLabel.setForeground(CYAN);
        rb.add(destLabel);
        rb.add(Box.createVerticalStrut(6));
        page9DestinationCombo = new JComboBox<>();
        styleDarkCombo(page9DestinationCombo);
        page9DestinationCombo.addItem("Find emergency services first...");
        page9DestinationCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        page9DestinationCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        rb.add(page9DestinationCombo);
        rb.add(Box.createVerticalStrut(10));

        // Bright green button with dark text so it is clearly visible on the dark panel.
        JButton route = button("🛣 FIND SAFE ROUTE");
        route.setBackground(new Color(40, 200, 120));
        route.setForeground(new Color(5, 20, 12));
        route.setFont(new Font("Segoe UI", Font.BOLD, 15));
        route.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(170, 255, 210), 2),
                new EmptyBorder(10, 18, 10, 18)));
        route.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        route.setAlignmentX(Component.LEFT_ALIGNMENT);
        route.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { route.setBackground(new Color(90, 235, 160)); }
            @Override public void mouseExited(MouseEvent e) { route.setBackground(new Color(40, 200, 120)); }
        });
        route.addActionListener(e -> calculateSafeRoute());
        rb.add(route);
        rb.add(Box.createVerticalStrut(8));

        page9RouteResultLabel = valueLabel("<html>ROUTE: No route calculated<br>Choose an emergency destination after loading nearby services.</html>");
        page9RouteResultLabel.setVerticalAlignment(SwingConstants.TOP);
        page9RouteResultLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rb.add(page9RouteResultLabel);
        rb.add(Box.createVerticalStrut(8));

        JLabel dirLabel = title("➤ ROUTE DIRECTIONS", 15);
        dirLabel.setForeground(GREEN);
        rb.add(dirLabel);
        rb.add(Box.createVerticalStrut(6));
        page9DirectionsArea = new JTextArea(9, 28);
        page9DirectionsArea.setEditable(false);
        page9DirectionsArea.setLineWrap(true);
        page9DirectionsArea.setWrapStyleWord(true);
        page9DirectionsArea.setBackground(new Color(8, 14, 24));
        page9DirectionsArea.setForeground(WHITE);
        page9DirectionsArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        page9DirectionsArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        page9DirectionsArea.setText("Turn-by-turn directions will appear here after you press FIND SAFE ROUTE.");
        JScrollPane dirScroll = new JScrollPane(page9DirectionsArea);
        dirScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        dirScroll.setBorder(BorderFactory.createLineBorder(new Color(39, 62, 88), 1));
        rb.add(dirScroll);

        JScrollPane rbScroll = new JScrollPane(rb);
        rbScroll.setBorder(null);
        rbScroll.getViewport().setBackground(PANEL);
        rbScroll.getVerticalScrollBar().setUnitIncrement(16);
        response.add(rbScroll, BorderLayout.CENTER);

        split.add(map);
        split.add(response);
        page.add(split, BorderLayout.CENTER);

        JPanel nav = new JPanel(new BorderLayout());
        nav.setBackground(BG);
        nav.add(backButton("P8"), BorderLayout.WEST);
        JPanel homePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        homePanel.setBackground(BG);
        JButton home = button("🏠 HOME");
        home.addActionListener(e -> cards.show(root, "P1"));
        homePanel.add(home);
        nav.add(homePanel, BorderLayout.EAST);
        page.add(nav, BorderLayout.SOUTH);

        return page;
    }

    // Dark combo box: readable text on a dark background (the default look is white-on-white).
    private void styleDarkCombo(JComboBox<String> c) {
        c.setUI(new javax.swing.plaf.basic.BasicComboBoxUI());
        c.setBackground(new Color(18, 29, 46));
        c.setForeground(WHITE);
        c.setFont(new Font("Segoe UI", Font.BOLD, 14));
        c.setBorder(BorderFactory.createLineBorder(CYAN, 1));
        c.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean sel, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
                l.setOpaque(true);
                l.setBackground(sel ? new Color(0, 120, 160) : new Color(18, 29, 46));
                l.setForeground(Color.WHITE);
                l.setBorder(new EmptyBorder(6, 10, 6, 10));
                return l;
            }
        });
    }

    private JPanel legendItem(Color dotColor, String label) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        p.setBackground(PANEL2);
        JLabel dot = new JLabel("●");
        dot.setForeground(dotColor);
        dot.setFont(new Font("Segoe UI", Font.BOLD, 17));
        JLabel name = new JLabel(label);
        name.setForeground(WHITE);
        name.setFont(new Font("Segoe UI", Font.BOLD, 11));
        p.add(dot);
        p.add(name);
        return p;
    }

    private Color serviceColor(String type) {
        return switch (type) {
            case "HOSPITAL" -> RED;
            case "SHELTER" -> GREEN;
            case "FIRE" -> ORANGE;
            case "POLICE" -> BLUE;
            default -> CYAN;
        };
    }

    private void updatePage9Context() {
        if (page9LocationLabel == null) return;
        page9LocationLabel.setText("LOCATION: " + placeName);
        page9CoordinatesLabel.setText("LAT/LON: " + formatCoord(latitude) + ", " + formatCoord(longitude));
        page9DisasterLabel.setText("DISASTER: " + detectedDisaster);
        page9RiskLabel.setText("STATUS: " + ("NO ACTIVE DISASTER".equals(detectedDisaster) ? "NORMAL" : "MONITOR"));
        page9RiskLevelLabel.setText("RISK LEVEL: " + riskLevel);
        if (page9MapView != null) page9MapView.repaint();
    }

    private void loadNearbyEmergencyServices() {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            JOptionPane.showMessageDialog(this,
                    "Current location is not available yet. Go to Page 3 and press GPS / CURRENT LOCATION first.",
                    "Location Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        page9ServicesArea.setText("Loading nearby hospitals, shelters, fire stations, police stations and rescue services...\nPlease wait.");
        emergencyPlaces.clear();
        if (page9DestinationCombo != null) {
            page9DestinationCombo.removeAllItems();
            page9DestinationCombo.addItem("Loading services...");
        }
        background.submit(() -> {
            try {
                String query = "[out:json][timeout:25];(" +
                        "nwr[\"amenity\"=\"hospital\"](around:12000," + latitude + "," + longitude + ");" +
                        "nwr[\"amenity\"=\"police\"](around:12000," + latitude + "," + longitude + ");" +
                        "nwr[\"amenity\"=\"fire_station\"](around:12000," + latitude + "," + longitude + ");" +
                        "nwr[\"amenity\"=\"shelter\"](around:12000," + latitude + "," + longitude + ");" +
                        "nwr[\"emergency\"=\"ambulance_station\"](around:12000," + latitude + "," + longitude + ");" +
                        ");out center tags;";

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "AI-Disaster-Management-College-Project/1.0")
                        .GET().build();
                String json = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

                emergencyPlaces.addAll(parseEmergencyPlaces(json));
                emergencyPlaces.sort(Comparator.comparingDouble(this::distanceKm));

                SwingUtilities.invokeLater(() -> {
                    page9DestinationCombo.removeAllItems();
                    StringBuilder out = new StringBuilder();
                    if (emergencyPlaces.isEmpty()) {
                        out.append("No nearby emergency services were returned by OpenStreetMap/Overpass.\n");
                        out.append("Try again or open the live map for a wider view.");
                        page9DestinationCombo.addItem("No services found");
                    } else {
                        out.append("NEARBY VERIFIED MAP SERVICES\n\n");
                        int count = 0;
                        for (EmergencyPlace ep : emergencyPlaces) {
                            if (count++ >= 12) break;
                            out.append("• ").append(ep.type).append(": ").append(ep.name)
                               .append(" — ").append(String.format(Locale.US, "%.2f km", distanceKm(ep))).append("\n");
                            page9DestinationCombo.addItem(ep.type + " — " + ep.name);
                        }
                    }
                    page9ServicesArea.setText(out.toString());
                    page9MapView.repaint();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    page9ServicesArea.setText("Could not load nearby services.\nCheck internet access and try again.\n\n" + ex.getMessage());
                    page9DestinationCombo.removeAllItems();
                    page9DestinationCombo.addItem("Service lookup failed");
                });
            }
        });
    }

    private ArrayList<EmergencyPlace> parseEmergencyPlaces(String json) {
        ArrayList<EmergencyPlace> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;

        // Overpass returns an elements array. Parse each element independently so
        // node/way/relation records do not get lost because of nested JSON braces.
        Pattern elementPattern = Pattern.compile("\\{\\s*\"type\"\\s*:\\s*\"(?:node|way|relation)\".*?(?=\\{\\s*\"type\"\\s*:|\\]\\s*\\})", Pattern.DOTALL);
        Matcher em = elementPattern.matcher(json);
        while (em.find()) {
            String object = em.group();
            String amenity = jsonString(object, "amenity");
            String emergency = jsonString(object, "emergency");
            String name = jsonString(object, "name");
            if (name == null || name.isBlank()) name = "Unnamed service";

            String type = null;
            if ("hospital".equals(amenity)) type = "HOSPITAL";
            else if ("police".equals(amenity)) type = "POLICE";
            else if ("fire_station".equals(amenity)) type = "FIRE";
            else if ("shelter".equals(amenity)) type = "SHELTER";
            else if ("ambulance_station".equals(emergency)) type = "RESCUE";
            if (type == null) continue;

            double lat = jsonNumber(object, "lat");
            double lon = jsonNumber(object, "lon");
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                int c = object.indexOf("\"center\"");
                if (c >= 0) {
                    String center = object.substring(c);
                    lat = jsonNumber(center, "lat");
                    lon = jsonNumber(center, "lon");
                }
            }
            if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                result.add(new EmergencyPlace(type, name, lat, lon));
            }
        }

        // Remove accidental duplicates from overlapping OSM records.
        LinkedHashMap<String, EmergencyPlace> unique = new LinkedHashMap<>();
        for (EmergencyPlace ep : result) {
            String key = ep.type + "|" + ep.name.toLowerCase(Locale.ROOT) + "|" +
                    String.format(Locale.US, "%.5f,%.5f", ep.lat, ep.lon);
            unique.putIfAbsent(key, ep);
        }
        return new ArrayList<>(unique.values());
    }

    private double distanceKm(EmergencyPlace ep) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) return Double.MAX_VALUE;
        double r = 6371.0;
        double dLat = Math.toRadians(ep.lat - latitude);
        double dLon = Math.toRadians(ep.lon - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(ep.lat)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private void calculateSafeRoute() {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            JOptionPane.showMessageDialog(this, "Current GPS location is required first.", "Location Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int idx = page9DestinationCombo.getSelectedIndex();
        if (idx < 0 || idx >= emergencyPlaces.size()) {
            loadNearbyEmergencyServices();
            JOptionPane.showMessageDialog(this, "Please wait for nearby services to load, then select a destination.", "Destination Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        EmergencyPlace dest = emergencyPlaces.get(idx);
        selectedDestination = dest;
        page9RouteResultLabel.setText("ROUTE: Calculating route to " + dest.name + "...");
        if (page9DirectionsArea != null) page9DirectionsArea.setText("Calculating directions...");
        background.submit(() -> {
            try {
                String url = "https://router.project-osrm.org/route/v1/driving/" +
                        longitude + "," + latitude + ";" + dest.lon + "," + dest.lat +
                        "?overview=full&geometries=geojson&steps=true";
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30)).header("User-Agent", "AI-Disaster-Management-College-Project/1.0").GET().build();
                String json = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
                // Route-level totals only (the old code picked the first "distance" in the JSON,
                // which belongs to a tiny first segment and was the same for every route).
                String routesPart = json;
                int wp = routesPart.indexOf("\"waypoints\"");
                if (wp > 0) routesPart = routesPart.substring(0, wp);
                double distanceMeters = lastJsonNumber(routesPart, "distance");
                double durationSeconds = lastJsonNumber(routesPart, "duration");
                java.util.List<Point2DData> pts = parseRouteCoordinates(json);
                if (Double.isNaN(distanceMeters) && pts.size() > 1) {
                    double sum = 0;
                    for (int i = 1; i < pts.size(); i++) sum += haversineKm(pts.get(i - 1).lat, pts.get(i - 1).lon, pts.get(i).lat, pts.get(i).lon);
                    distanceMeters = sum * 1000.0;
                }
                final String directionsText = buildDirections(json, dest);
                final double distanceMetersF = distanceMeters;
                final double durationSecondsF = durationSeconds;
                routePoints.clear();
                routePoints.addAll(pts);
                routeStatus = "Route found";
                SwingUtilities.invokeLater(() -> {
                    String warning = "Live road-closure data is not available in this demo, so verify official flood/road-closure alerts before travelling.";
                    page9RouteResultLabel.setText("<html>ROUTE: " + html(routeStatus) + "<br>" +
                            "DESTINATION: " + html(dest.name) + "<br>" +
                            "DISTANCE: " + (Double.isNaN(distanceMetersF) ? "--" : String.format(Locale.US, "%.2f km", distanceMetersF / 1000.0)) + "<br>" +
                            "ESTIMATED TIME: " + (Double.isNaN(durationSecondsF) ? "--" : formatDuration(durationSecondsF)) +
                            "<br><font color='#ff9a28'>⚠ " + html(warning) + "</font></html>");
                    if (page9DirectionsArea != null) {
                        page9DirectionsArea.setText(directionsText);
                        page9DirectionsArea.setCaretPosition(0);
                    }
                    page9MapView.repaint();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> page9RouteResultLabel.setText("<html>ROUTE FAILED<br>" + html(ex.getMessage()) + "</html>"));
            }
        });
    }

    // Builds readable turn-by-turn directions from the OSRM "steps".
    private String buildDirections(String json, EmergencyPlace dest) {
        StringBuilder out = new StringBuilder();
        out.append("START: Your current location\n");
        Matcher m = Pattern.compile("\"maneuver\"\\s*:\\s*\\{[^{}]*\\}").matcher(json);
        java.util.List<int[]> spans = new ArrayList<>();
        while (m.find()) spans.add(new int[]{m.start(), m.end()});
        int n = 1;
        for (int i = 0; i < spans.size(); i++) {
            String man = json.substring(spans.get(i)[0], spans.get(i)[1]);
            int segEnd = i + 1 < spans.size() ? spans.get(i + 1)[0] : json.length();
            String seg = json.substring(spans.get(i)[1], segEnd);
            String type = jsonString(man, "type");
            String mod = jsonString(man, "modifier");
            String road = jsonString(seg, "name");
            double d = jsonNumberInObject(seg, "distance");
            if (type == null) continue;
            if ("arrive".equals(type)) continue;
            String action;
            switch (type) {
                case "depart" -> action = "Start driving" + (mod != null ? " (" + mod + ")" : "");
                case "turn" -> action = "Turn " + (mod == null ? "" : mod);
                case "new name", "continue" -> action = "Continue" + (mod == null ? "" : " " + mod);
                case "merge" -> action = "Merge " + (mod == null ? "" : mod);
                case "on ramp", "off ramp" -> action = "Take the ramp " + (mod == null ? "" : mod);
                case "fork" -> action = "Keep " + (mod == null ? "" : mod) + " at the fork";
                case "end of road" -> action = "At the end of the road turn " + (mod == null ? "" : mod);
                case "roundabout", "rotary" -> action = "Enter the roundabout";
                case "roundabout turn" -> action = "At the roundabout turn " + (mod == null ? "" : mod);
                default -> action = type.substring(0, 1).toUpperCase() + type.substring(1) + (mod == null ? "" : " " + mod);
            }
            String roadText = (road == null || road.isBlank()) ? "" : " onto " + road;
            String dist = Double.isNaN(d) ? "" : (d >= 1000 ? String.format(Locale.US, " for %.1f km", d / 1000.0)
                    : String.format(Locale.US, " for %d m", Math.round(d)));
            out.append(n++).append(". ").append(action).append(roadText).append(dist).append("\n");
        }
        out.append("ARRIVE: ").append(dest.name).append(" (").append(dest.type).append(")");
        return out.toString();
    }

    private double lastJsonNumber(String json, String key) {
        String pattern = "\"" + key + "\"";
        int i = json.lastIndexOf(pattern);
        if (i < 0) return Double.NaN;
        int colon = json.indexOf(':', i + pattern.length());
        if (colon < 0) return Double.NaN;
        int s = colon + 1;
        while (s < json.length() && Character.isWhitespace(json.charAt(s))) s++;
        int e = s;
        while (e < json.length() && "0123456789+-.eE".indexOf(json.charAt(e)) >= 0) e++;
        try { return Double.parseDouble(json.substring(s, e)); } catch (Exception ex) { return Double.NaN; }
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * 6371.0 * Math.asin(Math.sqrt(a));
    }

    private java.util.List<Point2DData> parseRouteCoordinates(String json) {
        ArrayList<Point2DData> out = new ArrayList<>();
        int coord = json.indexOf("\"coordinates\"");
        if (coord < 0) return out;
        int end = json.indexOf("\"steps\"", coord);
        if (end < 0) end = json.length();
        String section = json.substring(coord, end);
        Pattern p = Pattern.compile("\\[\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\]");
        Matcher m = p.matcher(section);
        while (m.find()) {
            try { out.add(new Point2DData(Double.parseDouble(m.group(2)), Double.parseDouble(m.group(1)))); }
            catch (Exception ignored) {}
        }
        return out;
    }

    private String formatDuration(double seconds) {
        long mins = Math.max(0, Math.round(seconds / 60.0));
        long h = mins / 60;
        long m = mins % 60;
        return h > 0 ? h + " h " + m + " min" : m + " min";
    }

    private void openEmergencyBrowserMap() {
        try {
            startLocationServer();
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                requestRealLocation();
                return;
            }
            Desktop.getDesktop().browse(URI.create("http://localhost:8765/emergency"));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not open the live emergency map.\nOpen http://localhost:8765/emergency manually.",
                    "Emergency Map", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static class EmergencyPlace {
        final String type, name;
        final double lat, lon;
        EmergencyPlace(String type, String name, double lat, double lon) {
            this.type = type; this.name = name; this.lat = lat; this.lon = lon;
        }
    }

    private static class Point2DData {
        final double lat, lon;
        Point2DData(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }

    // ============================================================
    // REAL LOCATION SERVER
    // ============================================================

    private synchronized void startLocationServer() {
        if (locationServer != null) return;

        try {
            locationServer = HttpServer.create(new InetSocketAddress("localhost", 8765), 0);

            locationServer.createContext("/location", exchange -> {
                String html = locationHtml();
                send(exchange, html, "text/html; charset=UTF-8");
            });

            locationServer.createContext("/emergency", exchange -> {
                String html = emergencyHtml();
                send(exchange, html, "text/html; charset=UTF-8");
            });

            locationServer.createContext("/set-location", exchange -> {
                Map<String,String> q = parseQuery(exchange.getRequestURI().getRawQuery());

                String lat = q.get("lat");
                String lon = q.get("lon");

                if (lat != null && lon != null) {
                    try {
                        latitude = Double.parseDouble(lat);
                        longitude = Double.parseDouble(lon);

                        background.submit(() -> {
                            fetchAddressAndWeather(latitude, longitude);
                        });
                    } catch (NumberFormatException ignored) {}
                }

                send(exchange,
                        "{\"ok\":true}",
                        "application/json; charset=UTF-8");
            });

            locationServer.setExecutor(background);
            locationServer.start();

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not start local location service on port 8765.\n" +
                    "Try closing another copy of this application.",
                    "Location Service", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String emergencyHtml() {
        StringBuilder markers = new StringBuilder();
        for (EmergencyPlace ep : emergencyPlaces) {
            markers.append("L.marker([").append(ep.lat).append(",").append(ep.lon).append("])" )
                   .append(".addTo(map).bindPopup(\"").append(js(ep.type + " — " + ep.name)).append("<br>")
                   .append(String.format(Locale.US, "%.2f km", distanceKm(ep))).append("\");\n");
        }
        StringBuilder routeJs = new StringBuilder();
        if (!routePoints.isEmpty()) {
            routeJs.append("L.polyline([");
            for (Point2DData pt : routePoints) routeJs.append("[").append(pt.lat).append(",").append(pt.lon).append("],");
            routeJs.append("],{color:'#37dc82',weight:6}).addTo(map);");
        }
        return "<!DOCTYPE html>\n" +
                "<html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
                "<title>Emergency Response Map</title>\n" +
                "<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\">\n" +
                "<style>html,body,#map{height:100%;margin:0} body{background:#050911;font-family:Segoe UI,Arial;color:white} #top{position:absolute;z-index:9999;left:15px;top:15px;background:#0d1522dd;padding:14px 18px;border:1px solid #00d2ff;border-radius:10px;max-width:420px} #map{height:100vh}</style>\n" +
                "</head><body><div id=\"top\"><b style=\"color:#00d2ff\">🚨 EMERGENCY RESPONSE & SMART ROUTING</b><br>" +
                "Location: " + js(placeName) + "<br>Disaster: " + js(detectedDisaster) + " | Risk: " + riskPercentage + "%<br>" +
                "Use the Java window to load services and calculate a route. Always verify official road-closure alerts.</div>\n" +
                "<div id=\"map\"></div><script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n" +
                "<script>const map=L.map('map').setView([" + latitude + "," + longitude + "],14);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);\n" +
                "L.marker([" + latitude + "," + longitude + "]).addTo(map).bindPopup('<b>📍 You are here</b>').openPopup();\n" +
                markers + routeJs + "</script></body></html>";
    }

    private String js(String x) {
        if (x == null) return "";
        return x.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String locationHtml() {
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Real GPS Location</title>
<link rel="stylesheet"
 href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>
body{
 margin:0;background:#050911;color:#eef4fa;
 font-family:Segoe UI,Arial,sans-serif;
}
#top{padding:18px 22px;background:#0d1522;border-bottom:1px solid #27405a}
h1{margin:0;color:#00d2ff;font-size:24px}
p{color:#9badc1}
button{
 background:#142a42;color:white;border:1px solid #00d2ff;
 padding:12px 18px;border-radius:6px;font-weight:bold;
 cursor:pointer
}
#map{height:calc(100vh - 150px);min-height:450px}
#status{padding:10px 22px;color:#9badc1}
</style>
</head>
<body>
<div id="top">
<h1>📍 REAL-TIME LOCATION & INTERACTIVE MAP</h1>
<p>Allow browser location permission to send your actual coordinates to the Java application.</p>
<button onclick="locate()">USE MY CURRENT LOCATION</button>
<div id="status">Waiting for location permission...</div>
</div>
<div id="map"></div>

<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const map=L.map('map').setView([20,78],5);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
 maxZoom:19,
 attribution:'© OpenStreetMap contributors'
}).addTo(map);

let marker=null;

function status(t){
 document.getElementById('status').textContent=t;
}

function sendLocation(lat,lon){
 fetch('/set-location?lat='+encodeURIComponent(lat)+'&lon='+encodeURIComponent(lon))
 .then(()=>status('REAL LOCATION SENT TO JAVA: '+lat+', '+lon))
 .catch(()=>status('Location detected, but Java callback failed.'));
}

function show(lat,lon){
 if(marker) map.removeLayer(marker);
 marker=L.marker([lat,lon]).addTo(map)
   .bindPopup('<b>Current Location</b><br>'+lat.toFixed(6)+', '+lon.toFixed(6))
   .openPopup();
 map.setView([lat,lon],15);
 sendLocation(lat,lon);
}

function locate(){
 if(!navigator.geolocation){
   status('This browser does not support geolocation.');
   return;
 }
 status('Requesting real device location permission...');
 navigator.geolocation.getCurrentPosition(
   p=>{
     const lat=p.coords.latitude;
     const lon=p.coords.longitude;
     show(lat,lon);
   },
   e=>{
     status('Location permission/error: '+e.message);
   },
   {enableHighAccuracy:true,timeout:15000,maximumAge:0}
 );
}

map.on('click',e=>{
 show(e.latlng.lat,e.latlng.lng);
});

locate();
</script>
</body>
</html>
""";
    }

    // ============================================================
    // REAL REVERSE GEOCODING + WEATHER
    // ============================================================

    private void fetchAddressAndWeather(double lat, double lon) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .build();

            String geoUrl =
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
                    "&lat=" + URLEncoder.encode(String.valueOf(lat), StandardCharsets.UTF_8) +
                    "&lon=" + URLEncoder.encode(String.valueOf(lon), StandardCharsets.UTF_8) +
                    "&zoom=18&addressdetails=1";

            HttpRequest geoReq = HttpRequest.newBuilder()
                    .uri(URI.create(geoUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "AI-Disaster-Management-College-Project/1.0")
                    .GET().build();

            String geoJson = client.send(geoReq,
                    HttpResponse.BodyHandlers.ofString()).body();

            String display = jsonString(geoJson, "display_name");
            String city = jsonAddress(geoJson, "city");
            if (city == null) city = jsonAddress(geoJson, "town");
            if (city == null) city = jsonAddress(geoJson, "village");
            if (city == null) city = "Detected location";

            address = display == null ? "Address unavailable" : display;
            placeName = city;

            String weatherUrl =
                    "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=" + lat +
                    "&longitude=" + lon +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature," +
                    "precipitation,weather_code,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
                    "&timezone=auto";

            HttpRequest weatherReq = HttpRequest.newBuilder()
                    .uri(URI.create(weatherUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 AI-Disaster-Management-College-Project")
                    .GET().build();

            String weatherJson = client.send(weatherReq,
                    HttpResponse.BodyHandlers.ofString()).body();

            temperature = jsonNumber(weatherJson, "temperature_2m");
            feelsLike = jsonNumber(weatherJson, "apparent_temperature");
            humidity = (int) jsonNumber(weatherJson, "relative_humidity_2m");
            wind = jsonNumber(weatherJson, "wind_speed_10m");
            rainfall = jsonNumber(weatherJson, "precipitation");
            double[] rainProbabilities = dailyArray(weatherJson, "precipitation_probability_max");
            rainProbability = rainProbabilities.length > 0 ? (int) Math.round(rainProbabilities[0]) : -1;

            weatherCondition = weatherDescription((int) jsonNumber(weatherJson, "weather_code"));
            forecastText = buildForecast(weatherJson);

            SwingUtilities.invokeLater(this::updateLocationWeatherUI);

        } catch (Exception ex) {
            // If reverse geocoding fails, do NOT lose the weather data.
            // Fetch weather independently using the real GPS coordinates.
            try {
                fetchWeatherOnly(lat, lon);
                SwingUtilities.invokeLater(this::updateLocationWeatherUI);
            } catch (Exception weatherEx) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this,
                                "Real location was received, but live weather could not be fetched.\n\n" +
                                "Please check internet access and press REFRESH WEATHER.\n\n" + weatherEx.getMessage(),
                                "Weather API", JOptionPane.WARNING_MESSAGE));
            }
        }
    }

    private void fetchWeatherOnly(double lat, double lon) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12)).build();

        String weatherUrl = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=" + lat + "&longitude=" + lon +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
                "&timezone=auto";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(weatherUrl))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 AI-Disaster-Management-College-Project")
                .GET().build();

        String json = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        temperature = jsonNumber(json, "temperature_2m");
        feelsLike = jsonNumber(json, "apparent_temperature");
        humidity = (int) jsonNumber(json, "relative_humidity_2m");
        wind = jsonNumber(json, "wind_speed_10m");
        rainfall = jsonNumber(json, "precipitation");
        double[] rp = dailyArray(json, "precipitation_probability_max");
        rainProbability = rp.length > 0 ? (int)Math.round(rp[0]) : -1;
        weatherCondition = weatherDescription((int)jsonNumber(json, "weather_code"));
        forecastText = buildForecast(json);
    }

    private void updateLocationWeatherUI() {
        if (locationLabel != null)
            locationLabel.setText("Location: " + placeName);

        if (coordinatesLabel != null)
            coordinatesLabel.setText(
                    "Latitude: " + formatCoord(latitude) +
                    "   Longitude: " + formatCoord(longitude));

        if (addressLabel != null)
            addressLabel.setText("<html>Address: " + html(address) + "</html>");

        if (weatherLabel != null) {
            weatherLabel.setText(
                    "<html>" +
                    "Temperature: " + num(temperature) + " °C<br>" +
                    "Condition: " + weatherCondition + "<br>" +
                    "Feels-like: " + num(feelsLike) + " °C<br>" +
                    "Humidity: " + humidity + " %<br>" +
                    "Wind: " + num(wind) + " km/h<br>" +
                    "Rainfall/Precipitation: " + num(rainfall) + " mm" +
                    "</html>"
            );
        }

        if (forecastLabel != null)
            forecastLabel.setText("<html>" + forecastText + "</html>");

        updatePage9Context();
    }

    private void refreshWeather() {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            requestRealLocation();
            return;
        }
        background.submit(() -> fetchAddressAndWeather(latitude, longitude));
    }

    // ============================================================
    // SMALL JSON HELPERS
    // These are intentionally simple and dependency-free.
    // ============================================================

    private String jsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int i = json.indexOf(pattern);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + pattern.length());
        if (colon < 0) return null;
        int first = json.indexOf('"', colon + 1);
        if (first < 0) return null;
        int second = json.indexOf('"', first + 1);
        if (second < 0) return null;
        return json.substring(first + 1, second);
    }

    private String jsonAddress(String json, String key) {
        int address = json.indexOf("\"address\"");
        if (address < 0) return null;
        int keyIndex = json.indexOf("\"" + key + "\"", address);
        if (keyIndex < 0) return null;
        int colon = json.indexOf(':', keyIndex);
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private double jsonNumber(String json, String key) {
        // Open-Meteo returns the same field names inside current_units and current.
        // Always prefer the numeric value from the current object.
        int currentStart = json.indexOf("\"current\"");
        if (currentStart >= 0) {
            int objectStart = json.indexOf('{', currentStart);
            int objectEnd = json.indexOf('}', objectStart + 1);
            if (objectStart >= 0 && objectEnd > objectStart) {
                String current = json.substring(objectStart, objectEnd + 1);
                double value = jsonNumberInObject(current, key);
                if (!Double.isNaN(value)) return value;
            }
        }
        return jsonNumberInObject(json, key);
    }

    private double jsonNumberInObject(String json, String key) {
        String pattern = "\"" + key + "\"";
        int i = json.indexOf(pattern);
        while (i >= 0) {
            int colon = json.indexOf(':', i + pattern.length());
            if (colon < 0) return Double.NaN;
            int end = colon + 1;
            while (end < json.length() && Character.isWhitespace(json.charAt(end))) end++;
            int start = end;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (!(Character.isDigit(c) || c == '-' || c == '+' || c == '.')) break;
                end++;
            }
            if (end > start) {
                try { return Double.parseDouble(json.substring(start, end)); }
                catch (Exception ignored) { }
            }
            i = json.indexOf(pattern, i + pattern.length());
        }
        return Double.NaN;
    }

    private String buildForecast(String json) {
        // Dependency-free extraction of daily arrays for the first few days.
        // If an API response changes format, the current weather still works.
        String daily = json.substring(Math.max(0, json.indexOf("\"daily\"")));
        double[] max = arrayNumbers(daily, "temperature_2m_max");
        double[] min = arrayNumbers(daily, "temperature_2m_min");
        double[] rain = arrayNumbers(daily, "precipitation_probability_max");

        StringBuilder s = new StringBuilder("FORECAST<br><br>");
        int count = Math.min(5, Math.min(max.length, min.length));

        for (int i = 0; i < count; i++) {
            String rp = i < rain.length ? String.valueOf((int)rain[i]) + "%" : "--";
            s.append("Day ").append(i + 1)
             .append(": ").append(String.format(Locale.US, "%.1f", min[i]))
             .append("–").append(String.format(Locale.US, "%.1f", max[i]))
             .append(" °C | Rain: ").append(rp).append("<br>");
        }

        if (count == 0) s.append("Forecast unavailable");
        return s.toString();
    }

    // Reads an array from the "daily" section only. The same key also appears earlier in
    // "daily_units", which made the old code read the wrong array and return nothing.
    private double[] dailyArray(String json, String key) {
        int d = json.indexOf("\"daily\":");
        if (d < 0) d = json.indexOf("\"daily\"");
        if (d < 0) return new double[0];
        return arrayNumbers(json.substring(d), key);
    }

    private double[] arrayNumbers(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return new double[0];
        int open = json.indexOf('[', i);
        int close = json.indexOf(']', open);
        if (open < 0 || close < 0) return new double[0];

        String[] parts = json.substring(open + 1, close).split(",");
        ArrayList<Double> values = new ArrayList<>();

        for (String p : parts) {
            try { values.add(Double.parseDouble(p.trim())); }
            catch (Exception ignored) {}
        }

        double[] out = new double[values.size()];
        for (int j = 0; j < values.size(); j++) out[j] = values.get(j);
        return out;
    }

    private String weatherDescription(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1,2,3 -> "Mainly clear / cloudy";
            case 45,48 -> "Fog";
            case 51,53,55,56,57 -> "Drizzle";
            case 61,63,65,66,67 -> "Rain";
            case 71,73,75,77 -> "Snow";
            case 80,81,82 -> "Rain showers";
            case 85,86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96,99 -> "Thunderstorm with hail";
            default -> "Unknown weather";
        };
    }

    // ============================================================
    // HTTP / LOCATION UTILITIES
    // ============================================================

    private Map<String,String> parseQuery(String query) {
        Map<String,String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;

        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                try {
                    map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private void send(HttpExchange exchange, String body, String contentType)
            throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    // ============================================================
    // UI / MISC
    // ============================================================

    private void updateContexts() {
        if (safetyCombo != null) {
            if ("CYCLONE".equals(detectedDisaster)) safetyCombo.setSelectedItem("Cyclone");
            else if ("FLOOD".equals(detectedDisaster)) safetyCombo.setSelectedItem("Flood");
        }
        if (disasterContextLabel != null) {
            disasterContextLabel.setText(
                    "Detected Disaster: " + detectedDisaster +
                    "   |   Risk: " + riskPercentage +
                    "%   |   Level: " + riskLevel);
        }
        updatePage9Context();
    }

    private String formatCoord(double x) {
        return Double.isNaN(x) ? "--" : String.format(Locale.US, "%.6f", x);
    }

    private String num(double x) {
        return Double.isNaN(x) ? "--" : String.format(Locale.US, "%.1f", x);
    }

    private String html(String x) {
        if (x == null) return "";
        return x.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void shutdown() {
        if (locationServer != null) {
            locationServer.stop(0);
            locationServer = null;
        }
        background.shutdownNow();
    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            new DisasterManagementSystem().setVisible(true);
        });
    }
}
