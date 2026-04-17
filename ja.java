import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.Date;

/**
 * Oracle DB Data Transfer Tool v3
 */
public class OracleDataTransfer extends JFrame {

    // ── 폰트 상수 ────────────────────────────────────────────────────────────
    private static final String F_NAME = "맑은 고딕";
    private static final int F_SM = 13;
    private static final int F_MD = 14;
    private static final int F_LG = 16;
    private static final int F_XL = 19;

    // ── 색상 팔레트 ──────────────────────────────────────────────────────────
    private static final Color C_BG = new Color(0x0F, 0x17, 0x23);
    private static final Color C_PANEL = new Color(0x16, 0x21, 0x2E);
    private static final Color C_CARD = new Color(0x1C, 0x2A, 0x3A);
    private static final Color C_BORDER = new Color(0x2A, 0x3F, 0x55);
    private static final Color C_ACCENT = new Color(0x00, 0xC8, 0xFF);
    private static final Color C_ACCENT2 = new Color(0x00, 0xFF, 0xB3);
    private static final Color C_ERROR = new Color(0xFF, 0x45, 0x45);
    private static final Color C_SUCCESS = new Color(0x39, 0xD3, 0x53);
    private static final Color C_WARN = new Color(0xFF, 0xA0, 0x00);
    private static final Color C_TEXT = new Color(0xD0, 0xE8, 0xFF);
    private static final Color C_TEXT_DIM = new Color(0x5A, 0x7A, 0x9A);
    private static final Color C_INPUT_BG = new Color(0x0A, 0x12, 0x1C);
    private static final Color C_FIELD_BD = new Color(0x25, 0x45, 0x60);
    private static final Color C_DIFF_SRC = new Color(0x4A, 0x1A, 0x00);
    private static final Color C_DIFF_TGT = new Color(0x00, 0x2A, 0x4A);
    private static final Color C_DIFF_HL = new Color(0xFF, 0xD7, 0x00);

    // ── UI 필드 및 상태 ──────────────────────────────────────────────────────
    private JTextField srcIp, srcPort, srcSid, srcUser, srcSchema;
    private JPasswordField srcPass;
    private JButton btnConnSrc;
    private JLabel lblConnSrc;

    private JTextField tgtIp, tgtPort, tgtSid, tgtUser, tgtSchema;
    private JPasswordField tgtPass;
    private JButton btnConnTgt;
    private JLabel lblConnTgt;

    private final DefaultListModel<String> tableListModel = new DefaultListModel<>();
    private JList<String> tableList;
    private JTextField filterField;
    private JLabel lblTableCount;

    private JSpinner spinBatch;
    private JCheckBox chkDeleteAll, chkCommitEach;

    private DefaultTableModel progressModel;
    private JTable progressTable;
    private JProgressBar overallBar;
    private JLabel lblOverall;

    private JTextArea logArea;
    private JButton btnTransfer, btnStop, btnClear;

    private Connection srcConn = null;
    private Connection tgtConn = null;
    private volatile boolean stopRequested = false;
    private ExecutorService executor = null;

    public OracleDataTransfer() {
        super("Oracle Data Transfer v3 · 운영DB → 개발DB");
        initUI();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { onExit(); }
        });
    }

    private void initUI() {
        setSize(1500, 980);
        setMinimumSize(new Dimension(1280, 850));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(C_BG);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(C_PANEL);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, C_BORDER));
        p.setPreferredSize(new Dimension(0, 66));

        JLabel title = new JLabel("  ⬡  ORACLE DATA TRANSFER  v3");
        title.setFont(new Font(F_NAME, Font.BOLD, F_XL));
        title.setForeground(C_ACCENT);

        JLabel sub = new JLabel("Production → Development | CLOB 지원 · 스키마 지정 · 데이터 비교    ");
        sub.setFont(new Font(F_NAME, Font.PLAIN, F_SM));
        sub.setForeground(C_TEXT_DIM);

        p.add(title, BorderLayout.WEST);
        p.add(sub, BorderLayout.EAST);
        return p;
    }

    private JSplitPane buildCenter() {
        JSplitPane hs = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeftPanel(), buildRightPanel());
        hs.setDividerLocation(500);
        hs.setDividerSize(5);
        hs.setBackground(C_BG);
        hs.setBorder(null);
        return hs;
    }

    private JScrollPane buildLeftPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(C_BG);
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 8));

        p.add(buildDbCard("SOURCE  ( 운영 DB )", true));
        p.add(Box.createVerticalStrut(12));
        p.add(buildDbCard("TARGET  ( 개발 DB )", false));
        p.add(Box.createVerticalStrut(12));
        p.add(buildOptionsCard());
        p.add(Box.createVerticalGlue());

        JScrollPane sp = new JScrollPane(p);
        sp.setBorder(null);
        sp.setBackground(C_BG);
        sp.getViewport().setBackground(C_BG);
        sp.getVerticalScrollBar().setUnitIncrement(20);
        return sp;
    }

    private JPanel buildDbCard(String title, boolean isSource) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(C_CARD);
        card.setBorder(createCardBorder(title));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 310));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8);
        g.fill = GridBagConstraints.HORIZONTAL;

        String[] labels = {"Server IP", "Port", "SID", "Schema", "User ID", "Password"};
        int row = 0;

        if (isSource) {
            srcIp = makeField("192.168.1.10");
            srcPort = makeField("1521");
            srcSid = makeField("ORCL");
            srcSchema = makeField("SCOTT");
            srcUser = makeField("scott");
            srcPass = makePassField();
            JComponent[] comps = {srcIp, srcPort, srcSid, srcSchema, srcUser, srcPass};
            for (int i = 0; i < labels.length; i++) addFormRow(card, g, row++, labels[i], comps[i]);

            g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.weightx = 0;
            lblConnSrc = makeDotLabel(false);
            card.add(lblConnSrc, g);
            g.gridx = 1; g.gridwidth = 2; g.weightx = 1.0;
            btnConnSrc = makeButton("연결 테스트", C_ACCENT);
            btnConnSrc.addActionListener(e -> testConnection(true));
            card.add(btnConnSrc, g);
        } else {
            tgtIp = makeField("192.168.1.20");
            tgtPort = makeField("1521");
            tgtSid = makeField("ORCL");
            tgtSchema = makeField("SCOTT");
            tgtUser = makeField("scott");
            tgtPass = makePassField();
            JComponent[] comps = {tgtIp, tgtPort, tgtSid, tgtSchema, tgtUser, tgtPass};
            for (int i = 0; i < labels.length; i++) addFormRow(card, g, row++, labels[i], comps[i]);

            g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.weightx = 0;
            lblConnTgt = makeDotLabel(false);
            card.add(lblConnTgt, g);
            g.gridx = 1; g.gridwidth = 2; g.weightx = 1.0;
            btnConnTgt = makeButton("연결 테스트", C_ACCENT);
            btnConnTgt.addActionListener(e -> testConnection(false));
            card.add(btnConnTgt, g);
        }
        return card;
    }

    private void addFormRow(JPanel card, GridBagConstraints g, int row, String labelText, JComponent field) {
        g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.weightx = 0;
        JLabel lbl = new JLabel(labelText);
        lbl.setForeground(C_TEXT_DIM);
        lbl.setFont(new Font(F_NAME, Font.PLAIN, F_SM));
        lbl.setPreferredSize(new Dimension(78, 30));
        card.add(lbl, g);
        g.gridx = 1; g.gridwidth = 2; g.weightx = 1.0;
        card.add(field, g);
    }

    private JPanel buildOptionsCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(C_CARD);
        card.setBorder(createCardBorder("TRANSFER OPTIONS"));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 10, 6, 10);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; g.gridwidth = 1; g.weightx = 0;
        card.add(label("Batch Size"), g);
        g.gridx = 1; g.weightx = 1.0;
        spinBatch = new JSpinner(new SpinnerNumberModel(1000, 1, 100000, 500));
        styleSpinner(spinBatch);
        card.add(spinBatch, g);

        g.gridx = 0; g.gridy = 1; g.gridwidth = 2;
        chkDeleteAll = makeCheck("INSERT 전 TARGET 테이블 DELETE ALL (전체 삭제)");
        card.add(chkDeleteAll, g);

        g.gridy = 2;
        chkCommitEach = makeCheck("배치 단위로 COMMIT (기본: 테이블 단위 COMMIT)");
        chkCommitEach.setSelected(true);
        card.add(chkCommitEach, g);

        return card;
    }

    private JPanel buildRightPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(C_BG);
        p.setBorder(BorderFactory.createEmptyBorder(14, 8, 8, 14));

        JSplitPane vs = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildTablePanel(), buildProgressPanel());
        vs.setDividerLocation(340);
        vs.setDividerSize(5);
        vs.setBackground(C_BG);
        vs.setBorder(null);

        p.add(vs, BorderLayout.CENTER);
        p.add(buildLogPanel(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildTablePanel() {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(C_CARD);
        card.setBorder(createCardBorder("TABLE SELECTION ─ 더블클릭 또는 [미리보기] 버튼으로 데이터 확인"));

        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setBackground(C_CARD);
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 6, 6, 6));

        filterField = makeField("테이블명 입력 후 [목록 조회] (LIKE %입력값%)");
        filterField.addActionListener(e -> loadTables());

        JButton btnLoad = makeButton("목록 조회", C_ACCENT2);
        JButton btnSelAll = makeButton("전체 선택", C_TEXT_DIM);
        JButton btnDesel = makeButton("선택 해제", C_TEXT_DIM);
        JButton btnPreview = makeButton("⊞ 미리보기", C_WARN);

        btnLoad.setPreferredSize(new Dimension(105, 34));
        btnSelAll.setPreferredSize(new Dimension(95, 34));
        btnDesel.setPreferredSize(new Dimension(95, 34));
        btnPreview.setPreferredSize(new Dimension(115, 34));

        btnLoad.addActionListener(e -> loadTables());
        btnSelAll.addActionListener(e -> tableList.setSelectionInterval(0, tableListModel.getSize() - 1));
        btnDesel.addActionListener(e -> tableList.clearSelection());
        btnPreview.addActionListener(e -> openPreviewDialog());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        btns.setBackground(C_CARD);
        btns.add(btnLoad); btns.add(btnSelAll); btns.add(btnDesel); btns.add(btnPreview);

        toolbar.add(filterField, BorderLayout.CENTER);
        toolbar.add(btns, BorderLayout.EAST);

        tableList = new JList<>(tableListModel);
        tableList.setBackground(C_INPUT_BG);
        tableList.setForeground(C_TEXT);
        tableList.setFont(new Font(F_NAME, Font.PLAIN, F_MD));
        tableList.setSelectionBackground(new Color(0x00, 0x60, 0x90));
        tableList.setSelectionForeground(Color.WHITE);
        tableList.setFixedCellHeight(28);
        tableList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openPreviewDialog();
            }
        });

        JScrollPane sp = new JScrollPane(tableList);
        sp.setBorder(BorderFactory.createLineBorder(C_FIELD_BD));
        sp.getViewport().setBackground(C_INPUT_BG);

        lblTableCount = new JLabel("  테이블 수: 0");
        lblTableCount.setFont(new Font(F_NAME, Font.PLAIN, F_SM));
        lblTableCount.setForeground(C_TEXT_DIM);
        lblTableCount.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        card.add(toolbar, BorderLayout.NORTH);
        card.add(sp, BorderLayout.CENTER);
        card.add(lblTableCount, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildProgressPanel() {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(C_CARD);
        card.setBorder(createCardBorder("PROGRESS"));

        JPanel barPanel = new JPanel(new BorderLayout(10, 0));
        barPanel.setBackground(C_CARD);
        barPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        lblOverall = new JLabel("대기 중");
        lblOverall.setFont(new Font(F_NAME, Font.PLAIN, F_SM));
        lblOverall.setForeground(C_TEXT_DIM);
        lblOverall.setPreferredSize(new Dimension(160, 24));

        overallBar = new JProgressBar(0, 100);
        overallBar.setStringPainted(true);
        overallBar.setPreferredSize(new Dimension(0, 24));
        styleProgressBar(overallBar, C_ACCENT);

        barPanel.add(lblOverall, BorderLayout.WEST);
        barPanel.add(overallBar, BorderLayout.CENTER);

        String[] cols = {"테이블명", "상태", "총 건수", "이관 건수", "배치"};
        progressModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        progressTable = new JTable(progressModel);
        styleTable(progressTable);

        JScrollPane sp = new JScrollPane(progressTable);
        sp.setBorder(BorderFactory.createLineBorder(C_FIELD_BD));
        sp.getViewport().setBackground(C_INPUT_BG);

        card.add(barPanel, BorderLayout.NORTH);
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildLogPanel() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(C_CARD);
        card.setBorder(createCardBorder("LOG"));
        card.setPreferredSize(new Dimension(0, 180));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(C_INPUT_BG);
        logArea.setForeground(new Color(0x7F, 0xD6, 0x7F));
        logArea.setFont(new Font(F_NAME, Font.PLAIN, F_SM));
        logArea.setLineWrap(true);

        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(BorderFactory.createLineBorder(C_FIELD_BD));
        sp.getViewport().setBackground(C_INPUT_BG);
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFooter() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 12));
        p.setBackground(C_PANEL);
        p.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, C_BORDER));

        btnClear = makeButton("로그 지우기", C_TEXT_DIM);
        btnStop = makeButton("⏹ 중단", C_ERROR);
        btnTransfer = makeButton("▶ 데이터 이관 시작", C_ACCENT2);
        btnTransfer.setFont(new Font(F_NAME, Font.BOLD, F_LG));
        btnTransfer.setPreferredSize(new Dimension(210, 42));
        btnStop.setPreferredSize(new Dimension(110, 42));
        btnClear.setPreferredSize(new Dimension(120, 42));
        btnStop.setEnabled(false);

        btnClear.addActionListener(e -> logArea.setText(""));
        btnStop.addActionListener(e -> { stopRequested = true; log("⚠ 사용자 중단 요청..."); });
        btnTransfer.addActionListener(e -> startTransfer());

        p.add(btnClear);
        p.add(Box.createHorizontalStrut(24));
        p.add(btnStop);
        p.add(btnTransfer);
        return p;
    }

    private void openPreviewDialog() {
        String tableName = tableList.getSelectedValue();
        if (tableName == null) { showError("선택 없음", "미리볼 테이블을 선택하세요."); return; }

        JDialog dlg = new JDialog(this, "데이터 미리보기 ─ " + tableName, false);
        dlg.setSize(1300, 780);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(C_BG);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        top.setBackground(C_PANEL);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, C_BORDER));

        JLabel lblTbl = new JLabel("Table : " + tableName);
        lblTbl.setFont(new Font(F_NAME, Font.BOLD, F_LG));
        lblTbl.setForeground(C_ACCENT);

        JLabel lblRows = new JLabel("표시 건수 :");
        JSpinner spinRows = new JSpinner(new SpinnerNumberModel(100, 1, 5000, 50));
        styleSpinner(spinRows);

        JTextField fldWhere = makeField("(선택) 예) DEPT_ID = 10 AND STATUS = 'Y'");
        fldWhere.setPreferredSize(new Dimension(370, 32));

        JButton btnSrc = makeButton("◀ SOURCE 조회", C_ACCENT);
        JButton btnTgt = makeButton("▶ TARGET 조회", C_ACCENT2);
        JButton btnBoth = makeButton("◀▶ 양쪽 + 비교", C_WARN);
        JLabel lblStat = new JLabel("");

        top.add(lblTbl); top.add(lblRows); top.add(spinRows);
        top.add(new JLabel(" WHERE : ")); top.add(fldWhere);
        top.add(btnSrc); top.add(btnTgt); top.add(btnBoth); top.add(lblStat);

        JTabbedPane tabs = new JTabbedPane();
        styleTab(tabs);

        DefaultTableModel srcModel = new DefaultTableModel() { public boolean isCellEditable(int r, int c) { return false; } };
        JTable srcJT = buildPreviewTable(srcModel, false, null, null);
        JLabel srcCnt = makeCountLabel("SOURCE", C_ACCENT);
        JPanel srcPanel = wrapPreviewPanel(srcJT, srcCnt);

        DefaultTableModel tgtModel = new DefaultTableModel() { public boolean isCellEditable(int r, int c) { return false; } };
        JTable tgtJT = buildPreviewTable(tgtModel, false, null, null);
        JLabel tgtCnt = makeCountLabel("TARGET", C_ACCENT2);
        JPanel tgtPanel = wrapPreviewPanel(tgtJT, tgtCnt);

        DefaultTableModel diffModel = new DefaultTableModel() { public boolean isCellEditable(int r, int c) { return false; } };
        List<String> diffRowType = new ArrayList<>();
        List<Integer> diffColFlags = new ArrayList<>();
        JTable diffJT = buildPreviewTable(diffModel, true, diffRowType, diffColFlags);
        JLabel diffCnt = makeCountLabel("비교 결과", C_WARN);
        JPanel diffPanel = wrapPreviewPanel(diffJT, diffCnt);

        tabs.addTab("SOURCE", srcPanel);
        tabs.addTab("TARGET", tgtPanel);
        tabs.addTab("차이점 비교", diffPanel);

        List<List<Object>> cacheSource = new ArrayList<>();
        List<List<Object>> cacheTarget = new ArrayList<>();
        List<String> cacheColNames = new ArrayList<>();

        btnSrc.addActionListener(e -> {
            if (srcConn == null) { showError("오류", "SOURCE DB 연결 필요"); return; }
            execPreview(srcConn, getSchema(true), tableName, (int)spinRows.getValue(), fldWhere.getText(), srcModel, srcJT, srcCnt, lblStat, "SOURCE", cacheSource, cacheColNames);
        });
        
        btnTgt.addActionListener(e -> {
            if (tgtConn == null) { showError("오류", "TARGET DB 연결 필요"); return; }
            execPreview(tgtConn, getSchema(false), tableName, (int)spinRows.getValue(), fldWhere.getText(), tgtModel, tgtJT, tgtCnt, lblStat, "TARGET", cacheTarget, cacheColNames);
        });

        dlg.add(top, BorderLayout.NORTH);
        dlg.add(tabs, BorderLayout.CENTER);
        dlg.setVisible(true);
    }

    private void buildDiff(DefaultTableModel diffModel, JTable diffJT, JLabel diffCnt, List<String> diffRowType, List<Integer> diffColFlags, List<List<Object>> src, List<List<Object>> tgt, List<String> colNames) {
        diffModel.setRowCount(0);
        diffRowType.clear();
        int maxRows = Math.max(src.size(), tgt.size());
        Vector<String> headers = new Vector<>();
        headers.add("구분");
        headers.addAll(colNames);
        diffModel.setColumnIdentifiers(headers);

        for (int i = 0; i < maxRows; i++) {
            List<Object> srcRow = i < src.size() ? src.get(i) : null;
            List<Object> tgtRow = i < tgt.size() ? tgt.get(i) : null;
            if (srcRow == null) {
                Vector<Object> v = new Vector<>(); v.add("▶ TGT 추가"); v.addAll(tgtRow);
                diffModel.addRow(v); diffRowType.add("TGT_ONLY");
            } else if (tgtRow == null) {
                Vector<Object> v = new Vector<>(); v.add("◀ SRC 추가"); v.addAll(srcRow);
                diffModel.addRow(v); diffRowType.add("SRC_ONLY");
            } else {
                // 비교 로직 (생략 가능하나 구조 유지)
            }
        }
    }

    private JTable buildPreviewTable(DefaultTableModel model, boolean isDiff, List<String> rowTypeList, List<Integer> colFlagList) {
        JTable t = new JTable(model);
        t.setBackground(C_INPUT_BG); t.setForeground(C_TEXT);
        t.setRowHeight(26);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        return t;
    }

    private void fetchData(Connection conn, String schema, String tableName, int maxRows, String where, List<List<Object>> cache, List<String> colNamesOut) throws Exception {
        String qualified = (schema == null || schema.isBlank()) ? tableName : schema.toUpperCase() + "." + tableName;
        String sql = "SELECT * FROM " + qualified + (where == null || where.isBlank() ? "" : " WHERE " + where) + " FETCH FIRST " + maxRows + " ROWS ONLY";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCnt = meta.getColumnCount();
            if (colNamesOut.isEmpty()) { for (int i = 1; i <= colCnt; i++) colNamesOut.add(meta.getColumnName(i)); }
            cache.clear();
            while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= colCnt; i++) row.add(rs.getObject(i));
                cache.add(row);
            }
        }
    }

    private void execPreview(Connection conn, String schema, String tableName, int maxRows, String where, DefaultTableModel model, JTable jt, JLabel countLbl, JLabel statusLbl, String side, List<List<Object>> cacheOut, List<String> colNamesOut) {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                fetchData(conn, schema, tableName, maxRows, where, cacheOut, colNamesOut);
                return null;
            }
            protected void done() {
                // 모델 업데이트 및 UI 갱신
            }
        }.execute();
    }

    private void testConnection(boolean isSource) {
        String ip = (isSource ? srcIp : tgtIp).getText().trim();
        String port = (isSource ? srcPort : tgtPort).getText().trim();
        String sid = (isSource ? srcSid : tgtSid).getText().trim();
        String user = (isSource ? srcUser : tgtUser).getText().trim();
        String pass = new String((isSource ? srcPass : tgtPass).getPassword());

        new SwingWorker<Connection, Void>() {
            protected Connection doInBackground() throws Exception {
                return openConnection(ip, port, sid, user, pass);
            }
            protected void done() {
                try {
                    Connection c = get();
                    if (isSource) srcConn = c; else tgtConn = c;
                    setDot(isSource ? lblConnSrc : lblConnTgt, true);
                    log((isSource ? "[SOURCE]" : "[TARGET]") + " 연결 성공");
                } catch (Exception e) { log("연결 실패: " + e.getMessage()); }
            }
        }.execute();
    }

    private Connection openConnection(String ip, String port, String sid, String user, String pass) throws Exception {
        Class.forName("oracle.jdbc.driver.OracleDriver");
        String url = "jdbc:oracle:thin:@" + ip + ":" + port + "/" + sid;
        return DriverManager.getConnection(url, user, pass);
    }

    private void loadTables() {
        if (srcConn == null) return;
        new SwingWorker<List<String>, Void>() {
            protected List<String> doInBackground() throws Exception {
                List<String> list = new ArrayList<>();
                try (PreparedStatement ps = srcConn.prepareStatement("SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ?")) {
                    ps.setString(1, getSchema(true));
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) list.add(rs.getString(1));
                }
                return list;
            }
            protected void done() {
                try {
                    tableListModel.clear();
                    for (String t : get()) tableListModel.addElement(t);
                } catch (Exception e) { log("테이블 로드 실패"); }
            }
        }.execute();
    }

    private void startTransfer() {
        // 이관 로직 (Executor 사용)
    }

    private void onExit() {
        try { if (srcConn != null) srcConn.close(); if (tgtConn != null) tgtConn.close(); } catch (Exception e) {}
        System.exit(0);
    }

    // ── 유틸리티 메서드 ──────────────────────────────────────────────────────
    private String getSchema(boolean isSource) {
        String s = (isSource ? srcSchema : tgtSchema).getText().trim();
        return s.isEmpty() ? (isSource ? srcUser : tgtUser).getText().trim().toUpperCase() : s.toUpperCase();
    }
    private void log(String msg) { logArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + msg + "\n"); }
    private TitledBorder createCardBorder(String title) { return BorderFactory.createTitledBorder(BorderFactory.createLineBorder(C_BORDER), " " + title + " "); }
    private JTextField makeField(String txt) { return new JTextField(txt); }
    private JPasswordField makePassField() { return new JPasswordField(); }
    private JButton makeButton(String txt, Color fg) { JButton b = new JButton(txt); b.setForeground(fg); return b; }
    private JCheckBox makeCheck(String txt) { return new JCheckBox(txt); }
    private JLabel label(String txt) { return new JLabel(txt); }
    private JLabel makeDotLabel(boolean on) { JLabel l = new JLabel("●"); l.setForeground(on ? C_SUCCESS : C_ERROR); return l; }
    private void setDot(JLabel l, boolean on) { l.setForeground(on ? C_SUCCESS : C_ERROR); }
    private void styleSpinner(JSpinner s) {}
    private void styleProgressBar(JProgressBar p, Color c) {}
    private void styleTable(JTable t) {}
    private void styleTab(JTabbedPane t) {}
    private JLabel makeCountLabel(String s, Color c) { return new JLabel(s); }
    private JPanel wrapPreviewPanel(JTable t, JLabel l) { JPanel p = new JPanel(new BorderLayout()); p.add(new JScrollPane(t)); p.add(l, BorderLayout.NORTH); return p; }
    private void showError(String t, String m) { JOptionPane.showMessageDialog(this, m, t, JOptionPane.ERROR_MESSAGE); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OracleDataTransfer().setVisible(true));
    }
}
