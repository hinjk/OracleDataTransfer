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

/**
 * Oracle DB Data Transfer Tool  v3
 * 운영DB → 개발DB 데이터 이관 도구
 *
 * v3 변경사항 (v2 대비):
 *   1) CLOB / BLOB 컬럼 대응 (setCharacterStream / setBinaryStream)
 *   2) 폰트 → 맑은 고딕(Malgun Gothic)
 *   3) getConnection URL 구분자 ':' → '/'  (port/sid)
 *   4) TRUNCATE → DELETE FROM  (전체 삭제, 롤백 가능)
 *   5) FK DISABLE 체크박스 제거
 *   6) 테이블 필터 → ALL_TABLES LIKE %filter% DB 직접 조회
 *
 * 컴파일: javac -cp .:ojdbc8.jar OracleDataTransfer.java
 * 실행:   java  -cp .:ojdbc8.jar OracleDataTransfer
 * (Windows 는 ':'  →  ';')
 */
public class OracleDataTransfer extends JFrame {

    // ── 폰트 ─────────────────────────────────────────────────────────────────
    private static final String FN   = "맑은 고딕";
    private static final int    F_SM = 11;
    private static final int    F_MD = 12;
    private static final int    F_LG = 14;
    private static final int    F_TL = 17;

    // ── 색상 팔레트 ──────────────────────────────────────────────────────────
    private static final Color C_BG       = new Color(0x0F, 0x17, 0x23);
    private static final Color C_PANEL    = new Color(0x16, 0x21, 0x2E);
    private static final Color C_CARD     = new Color(0x1C, 0x2A, 0x3A);
    private static final Color C_BORDER   = new Color(0x2A, 0x3F, 0x55);
    private static final Color C_ACCENT   = new Color(0x00, 0xC8, 0xFF);
    private static final Color C_ACCENT2  = new Color(0x00, 0xFF, 0xB3);
    private static final Color C_ERROR    = new Color(0xFF, 0x45, 0x45);
    private static final Color C_SUCCESS  = new Color(0x39, 0xD3, 0x53);
    private static final Color C_WARN     = new Color(0xFF, 0xA0, 0x00);
    private static final Color C_TEXT     = new Color(0xD0, 0xE8, 0xFF);
    private static final Color C_TEXT_DIM = new Color(0x5A, 0x7A, 0x9A);
    private static final Color C_INPUT_BG = new Color(0x0A, 0x12, 0x1C);
    private static final Color C_FIELD_BD = new Color(0x25, 0x45, 0x60);

    // ── Source DB 필드 ────────────────────────────────────────────────────────
    private JTextField     srcIp, srcPort, srcSid, srcUser, srcSchema;
    private JPasswordField srcPass;
    private JButton        btnConnSrc;
    private JLabel         lblConnSrc;

    // ── Target DB 필드 ────────────────────────────────────────────────────────
    private JTextField     tgtIp, tgtPort, tgtSid, tgtUser, tgtSchema;
    private JPasswordField tgtPass;
    private JButton        btnConnTgt;
    private JLabel         lblConnTgt;

    // ── 테이블 선택 ───────────────────────────────────────────────────────────
    private final DefaultListModel<String> tableListModel = new DefaultListModel<>();
    private JList<String>  tableList;
    private JTextField     filterField;
    private JLabel         lblTableCount;

    // ── 옵션 ─────────────────────────────────────────────────────────────────
    private JSpinner  spinBatch;
    private JCheckBox chkDeleteAll;
    private JCheckBox chkCommitEach;

    // ── 진행상황 ──────────────────────────────────────────────────────────────
    private DefaultTableModel progressModel;
    private JTable            progressTable;
    private JProgressBar      overallBar;
    private JLabel            lblOverall;

    // ── 로그 ─────────────────────────────────────────────────────────────────
    private JTextArea logArea;

    // ── 버튼 ─────────────────────────────────────────────────────────────────
    private JButton btnTransfer, btnStop, btnClear;

    // ── 내부 상태 ─────────────────────────────────────────────────────────────
    private Connection       srcConn       = null;
    private Connection       tgtConn       = null;
    private volatile boolean stopRequested = false;
    private ExecutorService  executor      = null;

    // ─────────────────────────────────────────────────────────────────────────
    public OracleDataTransfer() {
        super("Oracle Data Transfer  v3  ·  운영DB → 개발DB");
        initUI();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { onExit(); }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  UI 구성
    // ═════════════════════════════════════════════════════════════════════════
    private void initUI() {
        setSize(1340, 900);
        setMinimumSize(new Dimension(1100, 780));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(C_BG);
        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildCenter(),  BorderLayout.CENTER);
        root.add(buildFooter(),  BorderLayout.SOUTH);
        setContentPane(root);
    }

    // ── 헤더 ─────────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(C_PANEL);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER));
        p.setPreferredSize(new Dimension(0, 56));

        JLabel title = new JLabel("  ⬡  ORACLE DATA TRANSFER  v3");
        title.setFont(new Font(FN, Font.BOLD, F_TL));
        title.setForeground(C_ACCENT);

        JLabel sub = new JLabel("Production → Development  |  CLOB 지원 · Schema 지정 · Batch Copy    ");
        sub.setFont(new Font(FN, Font.PLAIN, F_SM));
        sub.setForeground(C_TEXT_DIM);

        p.add(title, BorderLayout.WEST);
        p.add(sub,   BorderLayout.EAST);
        return p;
    }

    // ── 중앙 ─────────────────────────────────────────────────────────────────
    private JSplitPane buildCenter() {
        JSplitPane hs = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(), buildRightPanel());
        hs.setDividerLocation(445);
        hs.setDividerSize(4);
        hs.setBackground(C_BG);
        hs.setBorder(null);
        return hs;
    }

    // ── 왼쪽 패널 ────────────────────────────────────────────────────────────
    private JScrollPane buildLeftPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(C_BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 6));

        p.add(buildDbCard("SOURCE  ( 운영 DB )", true));
        p.add(Box.createVerticalStrut(10));
        p.add(buildDbCard("TARGET  ( 개발 DB )", false));
        p.add(Box.createVerticalStrut(10));
        p.add(buildOptionsCard());
        p.add(Box.createVerticalGlue());

        JScrollPane sp = new JScrollPane(p);
        sp.setBorder(null);
        sp.setBackground(C_BG);
        sp.getViewport().setBackground(C_BG);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    // ── DB 카드 ───────────────────────────────────────────────────────────────
    private JPanel buildDbCard(String title, boolean isSrc) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(C_CARD);
        card.setBorder(createCardBorder(title));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 265));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill   = GridBagConstraints.HORIZONTAL;

        String[] labels = {"Server IP", "Port", "SID", "Schema", "User ID", "Password"};
        int row = 0;

        if (isSrc) {
            srcIp     = makeField("192.168.1.10");
            srcPort   = makeField("1521");
            srcSid    = makeField("ORCL");
            srcSchema = makeField("SCOTT");
            srcUser   = makeField("scott");
            srcPass   = makePassField();
            JComponent[] cs = {srcIp, srcPort, srcSid, srcSchema, srcUser, srcPass};
            for (int i = 0; i < labels.length; i++) addFormRow(card, g, row++, labels[i], cs[i]);

            g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.weightx = 0;
            lblConnSrc = makeDotLabel(false);
            card.add(lblConnSrc, g);
            g.gridx = 1; g.gridwidth = 2; g.weightx = 1.0;
            btnConnSrc = makeButton("연결 테스트", C_ACCENT);
            btnConnSrc.addActionListener(e -> testConnection(true));
            card.add(btnConnSrc, g);
        } else {
            tgtIp     = makeField("192.168.1.20");
            tgtPort   = makeField("1521");
            tgtSid    = makeField("ORCL");
            tgtSchema = makeField("SCOTT");
            tgtUser   = makeField("scott");
            tgtPass   = makePassField();
            JComponent[] cs = {tgtIp, tgtPort, tgtSid, tgtSchema, tgtUser, tgtPass};
            for (int i = 0; i < labels.length; i++) addFormRow(card, g, row++, labels[i], cs[i]);

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

    private void addFormRow(JPanel card, GridBagConstraints g,
                            int row, String lbTxt, JComponent field) {
        g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.weightx = 0;
        JLabel lbl = new JLabel(lbTxt);
        lbl.setForeground(C_TEXT_DIM);
        lbl.setFont(new Font(FN, Font.PLAIN, F_SM));
        lbl.setPreferredSize(new Dimension(68, 24));
        card.add(lbl, g);
        g.gridx = 1; g.gridwidth = 2; g.weightx = 1.0;
        card.add(field, g);
    }

    // ── 옵션 카드 (FK 체크박스 없음, DELETE ALL) ──────────────────────────────
    private JPanel buildOptionsCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(C_CARD);
        card.setBorder(createCardBorder("TRANSFER OPTIONS"));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8);
        g.fill   = GridBagConstraints.HORIZONTAL;

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
        chkCommitEach = makeCheck("배치 단위로 COMMIT  (기본: 테이블 단위 COMMIT)");
        chkCommitEach.setSelected(true);
        card.add(chkCommitEach, g);

        return card;
    }

    // ── 오른쪽 패널 ───────────────────────────────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(C_BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 6, 6, 12));

        JSplitPane vs = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildTablePanel(), buildProgressPanel());
        vs.setDividerLocation(310);
        vs.setDividerSize(4);
        vs.setBackground(C_BG);
        vs.setBorder(null);

        p.add(vs,              BorderLayout.CENTER);
        p.add(buildLogPanel(), BorderLayout.SOUTH);
        return p;
    }

    // ── 테이블 선택 패널 ──────────────────────────────────────────────────────
    private JPanel buildTablePanel() {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(C_CARD);
        card.setBorder(createCardBorder(
                "TABLE SELECTION  ─  더블클릭 또는 [미리보기] 버튼으로 데이터 확인"));

        // 도구 모음
        JPanel toolbar = new JPanel(new BorderLayout(6, 0));
        toolbar.setBackground(C_CARD);
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));

        filterField = makeField("테이블명 입력 후 [목록 조회]  (LIKE  %입력값%)");
        filterField.addActionListener(e -> loadTables());   // Enter 키

        JButton btnLoad    = makeButton("목록 조회",  C_ACCENT2);
        JButton btnSelAll  = makeButton("전체 선택",  C_TEXT_DIM);
        JButton btnDesel   = makeButton("선택 해제",  C_TEXT_DIM);
        JButton btnPreview = makeButton("⊞ 미리보기", C_WARN);

        btnLoad.setPreferredSize(new Dimension(95, 28));
        btnSelAll.setPreferredSize(new Dimension(85, 28));
        btnDesel.setPreferredSize(new Dimension(80, 28));
        btnPreview.setPreferredSize(new Dimension(105, 28));

        btnLoad.addActionListener(e -> loadTables());
        btnSelAll.addActionListener(e -> tableList.setSelectionInterval(0, tableListModel.getSize() - 1));
        btnDesel.addActionListener(e -> tableList.clearSelection());
        btnPreview.addActionListener(e -> openPreviewDialog());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btns.setBackground(C_CARD);
        btns.add(btnLoad); btns.add(btnSelAll); btns.add(btnDesel); btns.add(btnPreview);

        toolbar.add(filterField, BorderLayout.CENTER);
        toolbar.add(btns,        BorderLayout.EAST);

        tableList = new JList<>(tableListModel);
        tableList.setBackground(C_INPUT_BG);
        tableList.setForeground(C_TEXT);
        tableList.setFont(new Font(FN, Font.PLAIN, F_MD));
        tableList.setSelectionBackground(new Color(0x00, 0x60, 0x90));
        tableList.setSelectionForeground(Color.WHITE);
        tableList.setFixedCellHeight(22);
        tableList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openPreviewDialog();
            }
        });

        JScrollPane sp = new JScrollPane(tableList);
        sp.setBorder(BorderFactory.createLineBorder(C_FIELD_BD));
        sp.getViewport().setBackground(C_INPUT_BG);

        lblTableCount = new JLabel("  테이블 수: 0");
        lblTableCount.setFont(new Font(FN, Font.PLAIN, F_SM));
        lblTableCount.setForeground(C_TEXT_DIM);
        lblTableCount.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        card.add(toolbar,       BorderLayout.NORTH);
        card.add(sp,            BorderLayout.CENTER);
        card.add(lblTableCount, BorderLayout.SOUTH);
        return card;
    }

    // ── 진행 상황 패널 ────────────────────────────────────────────────────────
    private JPanel buildProgressPanel() {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(C_CARD);
        card.setBorder(createCardBorder("PROGRESS"));

        JPanel barRow = new JPanel(new BorderLayout(8, 0));
        barRow.setBackground(C_CARD);
        barRow.setBorder(BorderFactory.createEmptyBorder(2, 6, 6, 6));

        lblOverall = new JLabel("대기 중");
        lblOverall.setFont(new Font(FN, Font.PLAIN, F_SM));
        lblOverall.setForeground(C_TEXT_DIM);
        lblOverall.setPreferredSize(new Dimension(140, 22));

        overallBar = new JProgressBar(0, 100);
        overallBar.setStringPainted(true);
        overallBar.setPreferredSize(new Dimension(0, 22));
        styleProgressBar(overallBar, C_ACCENT);

        barRow.add(lblOverall, BorderLayout.WEST);
        barRow.add(overallBar, BorderLayout.CENTER);

        String[] cols = {"테이블명", "상태", "총 건수", "이관 건수", "배치"};
        progressModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        progressTable = new JTable(progressModel);
        styleTable(progressTable);

        JScrollPane sp = new JScrollPane(progressTable);
        sp.setBorder(BorderFactory.createLineBorder(C_FIELD_BD));
        sp.getViewport().setBackground(C_INPUT_BG);

        card.add(barRow, BorderLayout.NORTH);
        card.add(sp,     BorderLayout.CENTER);
        return card;
    }

    // ── 로그 패널 ─────────────────────────────────────────────────────────────
    private JPanel buildLogPanel() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(C_CARD);
        card.setBorder(createCardBorder("LOG"));
        card.setPreferredSize(new Dimension(0, 155));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(C_INPUT_BG);
        logArea.setForeground(new Color(0x7F, 0xD6, 0x7F));
        logArea.setFont(new Font(FN, Font.PLAIN, F_SM));
        logArea.setLineWrap(true);

        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(BorderFactory.createLineBorder(C_FIELD_BD));
        sp.getViewport().setBackground(C_INPUT_BG);
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    // ── 푸터 ─────────────────────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        p.setBackground(C_PANEL);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, C_BORDER));

        btnClear    = makeButton("로그 지우기",         C_TEXT_DIM);
        btnStop     = makeButton("⏹  중단",             C_ERROR);
        btnTransfer = makeButton("▶  데이터 이관 시작",  C_ACCENT2);
        btnTransfer.setFont(new Font(FN, Font.BOLD, F_LG));
        btnTransfer.setPreferredSize(new Dimension(190, 36));
        btnStop.setPreferredSize(new Dimension(100, 36));
        btnClear.setPreferredSize(new Dimension(110, 36));
        btnStop.setEnabled(false);

        btnClear.addActionListener(e -> logArea.setText(""));
        btnStop.addActionListener(e -> { stopRequested = true; log("⚠ 사용자 중단 요청..."); });
        btnTransfer.addActionListener(e -> startTransfer());

        p.add(btnClear);
        p.add(Box.createHorizontalStrut(20));
        p.add(btnStop);
        p.add(btnTransfer);
        return p;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  데이터 미리보기 다이얼로그 (SOURCE / TARGET 탭)
    // ═════════════════════════════════════════════════════════════════════════
    private void openPreviewDialog() {
        String tableName = tableList.getSelectedValue();
        if (tableName == null) { showError("선택 없음", "미리볼 테이블을 선택하세요."); return; }

        JDialog dlg = new JDialog(this, "데이터 미리보기  ─  " + tableName, false);
        dlg.setSize(1150, 700);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(C_BG);

        // 상단 컨트롤 바
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        top.setBackground(C_PANEL);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER));

        JLabel lblTbl = new JLabel("Table :  " + tableName);
        lblTbl.setFont(new Font(FN, Font.BOLD, F_LG));
        lblTbl.setForeground(C_ACCENT);

        JLabel lblRows = new JLabel("표시 건수 :");
        lblRows.setFont(new Font(FN, Font.PLAIN, F_SM));
        lblRows.setForeground(C_TEXT_DIM);

        JSpinner spinRows = new JSpinner(new SpinnerNumberModel(100, 1, 5000, 50));
        styleSpinner(spinRows);
        spinRows.setPreferredSize(new Dimension(82, 26));

        JLabel lblWhere = new JLabel("WHERE :");
        lblWhere.setFont(new Font(FN, Font.PLAIN, F_SM));
        lblWhere.setForeground(C_TEXT_DIM);

        JTextField fldWhere = makeField("(선택)  예)  DEPT_ID = 10  AND  STATUS = 'Y'");
        fldWhere.setPreferredSize(new Dimension(360, 26));

        JButton btnSrc  = makeButton("◀  SOURCE 조회",  C_ACCENT);
        JButton btnTgt  = makeButton("▶  TARGET 조회",  C_ACCENT2);
        JButton btnBoth = makeButton("◀▶ 양쪽 동시 조회", C_WARN);
        JLabel  lblStat = new JLabel("");
        lblStat.setFont(new Font(FN, Font.PLAIN, F_SM));
        lblStat.setForeground(C_TEXT_DIM);

        top.add(lblTbl);
        top.add(Box.createHorizontalStrut(12));
        top.add(lblRows); top.add(spinRows);
        top.add(Box.createHorizontalStrut(8));
        top.add(lblWhere); top.add(fldWhere);
        top.add(Box.createHorizontalStrut(8));
        top.add(btnSrc); top.add(btnTgt); top.add(btnBoth);
        top.add(lblStat);

        // SOURCE / TARGET 탭
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font(FN, Font.BOLD, F_MD));
        styleTab(tabs);

        DefaultTableModel srcModel = new DefaultTableModel() {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable  srcJT    = buildPreviewTable(srcModel);
        JLabel  srcCount = makeCountLabel("SOURCE", C_ACCENT);
        JPanel  srcPanel = wrapPreviewPanel(srcJT, srcCount);

        DefaultTableModel tgtModel = new DefaultTableModel() {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable  tgtJT    = buildPreviewTable(tgtModel);
        JLabel  tgtCount = makeCountLabel("TARGET", C_ACCENT2);
        JPanel  tgtPanel = wrapPreviewPanel(tgtJT, tgtCount);

        tabs.addTab("◀  SOURCE  ( " + getSchema(true)  + "." + tableName + " )", srcPanel);
        tabs.addTab("▶  TARGET  ( " + getSchema(false) + "." + tableName + " )", tgtPanel);

        // 버튼 액션
        Runnable doSrc = () -> {
            if (srcConn == null) { showError("연결 없음", "SOURCE DB를 먼저 연결하세요."); return; }
            tabs.setSelectedIndex(0);
            lblStat.setForeground(C_ACCENT);
            lblStat.setText("SOURCE 조회 중...");
            execPreview(srcConn, getSchema(true), tableName,
                        (int) spinRows.getValue(), fldWhere.getText().trim(),
                        srcModel, srcJT, srcCount, lblStat, "SOURCE");
        };
        Runnable doTgt = () -> {
            if (tgtConn == null) { showError("연결 없음", "TARGET DB를 먼저 연결하세요."); return; }
            tabs.setSelectedIndex(1);
            lblStat.setForeground(C_ACCENT2);
            lblStat.setText("TARGET 조회 중...");
            execPreview(tgtConn, getSchema(false), tableName,
                        (int) spinRows.getValue(), fldWhere.getText().trim(),
                        tgtModel, tgtJT, tgtCount, lblStat, "TARGET");
        };

        btnSrc.addActionListener(e -> doSrc.run());
        btnTgt.addActionListener(e -> doTgt.run());
        btnBoth.addActionListener(e -> { doSrc.run(); doTgt.run(); });
        fldWhere.addActionListener(e -> { doSrc.run(); doTgt.run(); });

        // 닫기
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        bottom.setBackground(C_PANEL);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, C_BORDER));
        JButton btnClose = makeButton("닫기", C_TEXT_DIM);
        btnClose.addActionListener(e -> dlg.dispose());
        bottom.add(btnClose);

        dlg.setLayout(new BorderLayout());
        dlg.add(top,    BorderLayout.NORTH);
        dlg.add(tabs,   BorderLayout.CENTER);
        dlg.add(bottom, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // 미리보기 전용 JTable
    private JTable buildPreviewTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setBackground(C_INPUT_BG);
        t.setForeground(C_TEXT);
        t.setFont(new Font(FN, Font.PLAIN, F_SM));
        t.setGridColor(C_BORDER);
        t.setRowHeight(20);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.setSelectionBackground(new Color(0x00, 0x50, 0x80));
        t.setSelectionForeground(Color.WHITE);
        t.getTableHeader().setBackground(C_CARD);
        t.getTableHeader().setForeground(C_ACCENT);
        t.getTableHeader().setFont(new Font(FN, Font.BOLD, F_SM));
        t.getTableHeader().setBorder(BorderFactory.createLineBorder(C_BORDER));

        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable tbl, Object val,
                    boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(tbl, val, sel, foc, r, c);
                Color rowBg = (r % 2 == 0) ? C_INPUT_BG : new Color(0x0D, 0x18, 0x26);
                setBackground(sel ? new Color(0x00, 0x50, 0x80) : rowBg);
                if (val == null) {
                    setForeground(C_TEXT_DIM);
                    setText("(null)");
                    setFont(getFont().deriveFont(Font.ITALIC));
                } else {
                    setForeground(C_TEXT);
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
                return this;
            }
        });
        return t;
    }

    private JLabel makeCountLabel(String side, Color accent) {
        JLabel lbl = new JLabel("  " + side + "  —  조회 전");
        lbl.setFont(new Font(FN, Font.PLAIN, F_SM));
        lbl.setForeground(accent);
        lbl.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        return lbl;
    }

    private JPanel wrapPreviewPanel(JTable t, JLabel countLbl) {
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createLineBorder(C_FIELD_BD));
        sp.getViewport().setBackground(C_INPUT_BG);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(C_CARD);
        p.add(countLbl, BorderLayout.NORTH);
        p.add(sp,       BorderLayout.CENTER);
        return p;
    }

    private void styleTab(JTabbedPane tabs) {
        tabs.setBackground(C_BG);
        tabs.setForeground(C_TEXT);
        UIManager.put("TabbedPane.selected",             C_CARD);
        UIManager.put("TabbedPane.background",           C_BG);
        UIManager.put("TabbedPane.foreground",           C_TEXT);
        UIManager.put("TabbedPane.unselectedBackground", C_PANEL);
    }

    // SELECT 실행 → 모델에 채우기
    private void execPreview(Connection conn, String schema, String tableName,
                             int maxRows, String where,
                             DefaultTableModel model, JTable jt,
                             JLabel countLbl, JLabel statusLbl, String side) {
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            int rowCount = 0;
            String errMsg;

            protected Void doInBackground() {
                String qualified = (schema == null || schema.isBlank())
                        ? tableName : schema.toUpperCase() + "." + tableName;
                String sql = "SELECT * FROM " + qualified
                        + (where == null || where.isBlank() ? "" : " WHERE " + where)
                        + " FETCH FIRST " + maxRows + " ROWS ONLY";

                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(sql)) {

                    ResultSetMetaData meta = rs.getMetaData();
                    int colCnt = meta.getColumnCount();

                    Vector<String> headers = new Vector<>();
                    for (int i = 1; i <= colCnt; i++)
                        headers.add(meta.getColumnName(i)
                                + "\n[" + meta.getColumnTypeName(i) + "]");

                    Vector<Vector<Object>> data = new Vector<>();
                    while (rs.next()) {
                        Vector<Object> row = new Vector<>();
                        for (int i = 1; i <= colCnt; i++) {
                            int type = meta.getColumnType(i);
                            if (type == Types.CLOB || type == Types.NCLOB) {
                                Clob clob = rs.getClob(i);
                                if (clob == null) { row.add(null); continue; }
                                long len = clob.length();
                                String s = clob.getSubString(1, (int) Math.min(len, 500));
                                row.add(len > 500 ? s + " …[" + len + "자]" : s);
                            } else {
                                row.add(rs.getObject(i));
                            }
                        }
                        data.add(row);
                    }
                    rowCount = data.size();
                    SwingUtilities.invokeLater(() -> {
                        model.setDataVector(data, headers);
                        autoResizeColumns(jt, colCnt);
                    });
                } catch (Exception ex) { errMsg = ex.getMessage(); }
                return null;
            }

            protected void done() {
                if (errMsg != null) {
                    statusLbl.setForeground(C_ERROR);
                    statusLbl.setText("오류: " + errMsg);
                    countLbl.setText("  " + side + "  —  오류");
                } else {
                    statusLbl.setForeground(C_SUCCESS);
                    statusLbl.setText(side + " 조회 완료  (" + String.format("%,d", rowCount) + "건)");
                    countLbl.setText(String.format("  %s  —  %,d 건 표시  (최대 %,d)",
                            side, rowCount, maxRows));
                    log(String.format("[PREVIEW] %s  %s.%s  %,d건 조회",
                            side, schema, tableName, rowCount));
                }
            }
        };
        w.execute();
    }

    private void autoResizeColumns(JTable t, int colCnt) {
        if (t == null) return;
        TableColumnModel cm = t.getColumnModel();
        for (int c = 0; c < colCnt && c < cm.getColumnCount(); c++) {
            TableColumn col = cm.getColumn(c);
            int w = 80;
            TableCellRenderer hr = t.getTableHeader().getDefaultRenderer();
            Component hc = hr.getTableCellRendererComponent(
                    t, col.getHeaderValue(), false, false, -1, c);
            w = Math.max(w, hc.getPreferredSize().width + 10);
            int sample = Math.min(50, t.getRowCount());
            for (int r = 0; r < sample; r++) {
                TableCellRenderer cr = t.getCellRenderer(r, c);
                Component cc = t.prepareRenderer(cr, r, c);
                w = Math.max(w, cc.getPreferredSize().width + 10);
            }
            col.setPreferredWidth(Math.min(w, 280));
        }
    }

    private String getSchema(boolean isSrc) {
        String s = (isSrc ? srcSchema : tgtSchema).getText().trim();
        return s.isEmpty()
            ? (isSrc ? srcUser : tgtUser).getText().trim().toUpperCase()
            : s.toUpperCase();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  연결 테스트
    // ═════════════════════════════════════════════════════════════════════════
    private void testConnection(boolean isSrc) {
        JButton btn = isSrc ? btnConnSrc : btnConnTgt;
        JLabel  lbl = isSrc ? lblConnSrc : lblConnTgt;
        btn.setEnabled(false);
        btn.setText("연결 중...");

        String ip     = (isSrc ? srcIp     : tgtIp    ).getText().trim();
        String port   = (isSrc ? srcPort   : tgtPort  ).getText().trim();
        String sid    = (isSrc ? srcSid    : tgtSid   ).getText().trim();
        String user   = (isSrc ? srcUser   : tgtUser  ).getText().trim();
        String schema = (isSrc ? srcSchema : tgtSchema).getText().trim();
        String pass   = new String(isSrc ? srcPass.getPassword() : tgtPass.getPassword());

        SwingWorker<Connection, Void> w = new SwingWorker<>() {
            String err;
            protected Connection doInBackground() {
                try { return openConnection(ip, port, sid, user, pass); }
                catch (Exception ex) { err = ex.getMessage(); return null; }
            }
            protected void done() {
                btn.setEnabled(true);
                btn.setText("연결 테스트");
                try {
                    Connection c = get();
                    if (c != null) {
                        if (isSrc) srcConn = c; else tgtConn = c;
                        setDot(lbl, true);
                        log((isSrc ? "[SOURCE]" : "[TARGET]")
                            + " 연결 성공: " + user + "@" + ip + ":" + port + "/" + sid
                            + (schema.isEmpty() ? "" : "  Schema=" + schema.toUpperCase()));
                        showInfo("연결 성공",
                                (isSrc ? "SOURCE" : "TARGET") + " DB에 연결되었습니다.");
                    } else {
                        setDot(lbl, false);
                        log("❌ 연결 실패: " + err);
                        showError("연결 실패", err);
                    }
                } catch (Exception ex) { log("❌ " + ex.getMessage()); }
            }
        };
        w.execute();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  테이블 목록 조회  ─  LIKE %filter% DB 직접 조회
    // ═════════════════════════════════════════════════════════════════════════
    private void loadTables() {
        if (srcConn == null) { showError("오류", "SOURCE DB를 먼저 연결하세요."); return; }

        String schema = getSchema(true);
        String raw    = filterField.getText().trim();
        // 안내 문구는 필터로 쓰지 않음
        String filter = raw.startsWith("테이블명 입력") ? "" : raw;

        tableListModel.clear();
        final String ff = filter;
        log("[TABLE] 스키마 '" + schema + "'"
            + (ff.isEmpty() ? "" : "  필터: %" + ff + "%") + "  조회 중...");

        SwingWorker<List<String>, Void> w = new SwingWorker<>() {
            protected List<String> doInBackground() throws Exception {
                List<String> list = new ArrayList<>();
                String like = ff.isEmpty() ? "%" : "%" + ff.toUpperCase() + "%";

                // ALL_TABLES
                String sql = "SELECT TABLE_NAME FROM ALL_TABLES " +
                             "WHERE OWNER = ? AND TABLE_NAME LIKE ? ORDER BY TABLE_NAME";
                try (PreparedStatement ps = srcConn.prepareStatement(sql)) {
                    ps.setString(1, schema);
                    ps.setString(2, like);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) list.add(rs.getString(1));
                    }
                }
                // 결과 없으면 USER_TABLES 폴백
                if (list.isEmpty()) {
                    String sql2 = "SELECT TABLE_NAME FROM USER_TABLES " +
                                  "WHERE TABLE_NAME LIKE ? ORDER BY TABLE_NAME";
                    try (PreparedStatement ps = srcConn.prepareStatement(sql2)) {
                        ps.setString(1, like);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) list.add(rs.getString(1));
                        }
                    }
                }
                return list;
            }
            protected void done() {
                try {
                    List<String> list = get();
                    for (String t : list) tableListModel.addElement(t);
                    lblTableCount.setText("  테이블 수: " + list.size()
                        + "  (Schema: " + schema + ")"
                        + (ff.isEmpty() ? "" : "  필터: %" + ff + "%"));
                    log("[TABLE] " + list.size() + "개 로드  (Schema: " + schema + ")");
                } catch (Exception ex) {
                    log("❌ 테이블 조회 실패: " + ex.getMessage());
                }
            }
        };
        w.execute();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  데이터 이관  ─  CLOB / BLOB 대응 · DELETE ALL
    // ═════════════════════════════════════════════════════════════════════════
    private void startTransfer() {
        if (srcConn == null || tgtConn == null) {
            showError("오류", "SOURCE / TARGET DB를 모두 연결한 후 시작하세요."); return;
        }
        List<String> selected = tableList.getSelectedValuesList();
        if (selected.isEmpty()) {
            showError("오류", "이관할 테이블을 하나 이상 선택하세요."); return;
        }

        String srcSch = getSchema(true);
        String tgtSch = getSchema(false);

        int confirm = JOptionPane.showConfirmDialog(this,
            String.format("총 %d개 테이블을 이관합니다.\n\nSOURCE Schema : %s\nTARGET Schema : %s\n\n계속하시겠습니까?",
                selected.size(), srcSch, tgtSch),
            "이관 확인", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        progressModel.setRowCount(0);
        for (String t : selected)
            progressModel.addRow(new Object[]{t, "대기", "-", "-", "-"});

        btnTransfer.setEnabled(false);
        btnStop.setEnabled(true);
        stopRequested = false;
        overallBar.setValue(0);
        lblOverall.setText("이관 중...");

        int     batchSize  = (int) spinBatch.getValue();
        boolean deleteAll  = chkDeleteAll.isSelected();
        boolean commitEach = chkCommitEach.isSelected();

        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            AtomicInteger done = new AtomicInteger(0);
            for (int idx = 0; idx < selected.size(); idx++) {
                if (stopRequested) { log("⚠ 중단됨"); break; }
                String table = selected.get(idx);
                final int row = idx;
                updateProgress(row, "진행 중", "-", "0", "-");
                try {
                    transferTable(table, srcSch, tgtSch, batchSize, deleteAll, commitEach, row);
                    updateProgress(row, "✔ 완료", null, null, null);
                } catch (Exception ex) {
                    updateProgress(row, "❌ 오류", "-", "-", "-");
                    log("❌ [" + table + "] 오류: " + ex.getMessage());
                }
                int d   = done.incrementAndGet();
                int pct = (int)((d * 100.0) / selected.size());
                SwingUtilities.invokeLater(() -> {
                    overallBar.setValue(pct);
                    lblOverall.setText(d + " / " + selected.size() + " 완료");
                });
            }
            SwingUtilities.invokeLater(() -> {
                btnTransfer.setEnabled(true);
                btnStop.setEnabled(false);
                log(stopRequested ? "⚠ 이관이 중단되었습니다." : "✅ 모든 이관 완료!");
            });
        });
    }

    private void transferTable(String table, String srcSch, String tgtSch,
                               int batchSize, boolean deleteAll,
                               boolean commitEach, int row) throws Exception {
        String srcQ = srcSch.isBlank() ? table : srcSch + "." + table;
        String tgtQ = tgtSch.isBlank() ? table : tgtSch + "." + table;
        log("[START] " + srcQ + "  →  " + tgtQ);

        // 총 건수
        long total = 0;
        try (Statement st = srcConn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + srcQ)) {
            if (rs.next()) total = rs.getLong(1);
        }
        final long totalF = total;
        SwingUtilities.invokeLater(() ->
            progressModel.setValueAt(String.format("%,d", totalF), row, 2));

        if (total == 0) {
            log("[SKIP] " + srcQ + " — 데이터 없음");
            updateProgress(row, "✔ 완료(0건)", "0", "0", "0");
            return;
        }

        // 컬럼명 + 타입
        List<String>  cols     = new ArrayList<>();
        List<Integer> colTypes = new ArrayList<>();
        fetchColumnMeta(srcConn, srcSch, table, cols, colTypes);
        if (cols.isEmpty()) fetchColumnMeta(srcConn, null, table, cols, colTypes);

        String colList   = String.join(",", cols);
        String params    = String.join(",", Collections.nCopies(cols.size(), "?"));
        String insertSql = "INSERT INTO " + tgtQ + " (" + colList + ") VALUES (" + params + ")";
        String selectSql = "SELECT " + colList + " FROM " + srcQ;

        tgtConn.setAutoCommit(false);

        // DELETE ALL
        if (deleteAll) {
            try (Statement st = tgtConn.createStatement()) {
                int deleted = st.executeUpdate("DELETE FROM " + tgtQ);
                tgtConn.commit();
                log("[DELETE] " + tgtQ + "  " + String.format("%,d", deleted) + "건 삭제");
            }
        }

        long inserted = 0;
        int  batchNum = 0;

        try (Statement srcSt = srcConn.createStatement(
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = srcSt.executeQuery(selectSql);
             PreparedStatement ps = tgtConn.prepareStatement(insertSql)) {

            srcSt.setFetchSize(batchSize);

            while (rs.next() && !stopRequested) {
                for (int c = 0; c < cols.size(); c++) {
                    int sqlType = colTypes.get(c);
                    if (sqlType == Types.CLOB || sqlType == Types.NCLOB) {
                        Clob clob = rs.getClob(c + 1);
                        if (clob == null) ps.setNull(c + 1, sqlType);
                        else              ps.setCharacterStream(c + 1,
                                              clob.getCharacterStream(), (int) clob.length());
                    } else if (sqlType == Types.BLOB) {
                        Blob blob = rs.getBlob(c + 1);
                        if (blob == null) ps.setNull(c + 1, Types.BLOB);
                        else              ps.setBinaryStream(c + 1,
                                              blob.getBinaryStream(), (int) blob.length());
                    } else {
                        ps.setObject(c + 1, rs.getObject(c + 1));
                    }
                }
                ps.addBatch();
                inserted++;

                if (inserted % batchSize == 0) {
                    ps.executeBatch();
                    if (commitEach) tgtConn.commit();
                    batchNum++;
                    final long ins = inserted; final int bn = batchNum;
                    SwingUtilities.invokeLater(() -> {
                        progressModel.setValueAt(String.format("%,d", ins), row, 3);
                        progressModel.setValueAt(String.valueOf(bn), row, 4);
                    });
                    log(String.format("  [%s] 배치 #%d  (%,d / %,d)",
                            table, batchNum, inserted, total));
                }
            }
            if (inserted % batchSize != 0) { ps.executeBatch(); batchNum++; }
            tgtConn.commit();
        } catch (Exception ex) {
            tgtConn.rollback();
            throw ex;
        } finally {
            tgtConn.setAutoCommit(true);
        }

        final long insF = inserted; final int bnF = batchNum;
        SwingUtilities.invokeLater(() -> {
            progressModel.setValueAt(String.format("%,d", insF), row, 3);
            progressModel.setValueAt(String.valueOf(bnF), row, 4);
        });
        log(String.format("[DONE] %s  총 %,d건 / %d 배치", table, inserted, batchNum));
    }

    private void fetchColumnMeta(Connection conn, String schema, String table,
                                 List<String> colsOut, List<Integer> typesOut) throws Exception {
        String owner = (schema == null || schema.isBlank()) ? null : schema.toUpperCase();
        try (ResultSet rs = conn.getMetaData().getColumns(null, owner, table.toUpperCase(), null)) {
            while (rs.next()) {
                colsOut.add(rs.getString("COLUMN_NAME"));
                typesOut.add(rs.getInt("DATA_TYPE"));
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  공통 유틸
    // ═════════════════════════════════════════════════════════════════════════
    /** URL: jdbc:oracle:thin:@ip:port/sid  (슬래시 구분자) */
    private Connection openConnection(String ip, String port, String sid,
                                      String user, String pass) throws Exception {
        try { Class.forName("oracle.jdbc.driver.OracleDriver"); }
        catch (ClassNotFoundException e) {
            throw new Exception(
                "ojdbc jar가 클래스패스에 없습니다.\n" +
                "ojdbc8.jar 를 같은 폴더에 두고\n" +
                "java -cp .:ojdbc8.jar OracleDataTransfer  로 실행하세요.");
        }
        return DriverManager.getConnection(
            "jdbc:oracle:thin:@" + ip + ":" + port + "/" + sid, user, pass);
    }

    private void updateProgress(int row, String status,
                                 String total, String ins, String batch) {
        SwingUtilities.invokeLater(() -> {
            if (status != null) progressModel.setValueAt(status, row, 1);
            if (total  != null) progressModel.setValueAt(total,  row, 2);
            if (ins    != null) progressModel.setValueAt(ins,    row, 3);
            if (batch  != null) progressModel.setValueAt(batch,  row, 4);
        });
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date())
                           + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void onExit() {
        if (executor != null) executor.shutdownNow();
        try { if (srcConn != null) srcConn.close(); } catch (Exception ignored) {}
        try { if (tgtConn != null) tgtConn.close(); } catch (Exception ignored) {}
        System.exit(0);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  스타일 헬퍼
    // ═════════════════════════════════════════════════════════════════════════
    private TitledBorder createCardBorder(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(C_BORDER), "  " + title + "  ");
        tb.setTitleColor(C_ACCENT);
        tb.setTitleFont(new Font(FN, Font.BOLD, F_SM));
        return tb;
    }

    private JTextField makeField(String placeholder) {
        JTextField f = new JTextField(placeholder);
        f.setBackground(C_INPUT_BG); f.setForeground(C_TEXT);
        f.setCaretColor(C_ACCENT);
        f.setFont(new Font(FN, Font.PLAIN, F_MD));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(C_FIELD_BD),
            BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        f.setPreferredSize(new Dimension(0, 26));
        return f;
    }

    private JPasswordField makePassField() {
        JPasswordField f = new JPasswordField();
        f.setBackground(C_INPUT_BG); f.setForeground(C_TEXT);
        f.setCaretColor(C_ACCENT);
        f.setFont(new Font(FN, Font.PLAIN, F_MD));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(C_FIELD_BD),
            BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        f.setPreferredSize(new Dimension(0, 26));
        return f;
    }

    private JButton makeButton(String text, Color fg) {
        JButton b = new JButton(text);
        b.setForeground(fg); b.setBackground(C_CARD);
        b.setFont(new Font(FN, Font.BOLD, F_SM));
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(fg.darker()),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(fg.darker().darker()); }
            public void mouseExited(MouseEvent e)  { b.setBackground(C_CARD); }
        });
        return b;
    }

    private JCheckBox makeCheck(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setBackground(C_CARD); cb.setForeground(C_TEXT);
        cb.setFont(new Font(FN, Font.PLAIN, F_MD));
        cb.setFocusPainted(false);
        return cb;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(C_TEXT_DIM);
        l.setFont(new Font(FN, Font.PLAIN, F_SM));
        return l;
    }

    private JLabel makeDotLabel(boolean on) {
        JLabel l = new JLabel("●");
        l.setFont(new Font("Dialog", Font.PLAIN, 14));
        l.setForeground(on ? C_SUCCESS : C_ERROR);
        return l;
    }

    private void setDot(JLabel lbl, boolean on) {
        SwingUtilities.invokeLater(() -> lbl.setForeground(on ? C_SUCCESS : C_ERROR));
    }

    private void styleSpinner(JSpinner s) {
        s.setBackground(C_INPUT_BG); s.setForeground(C_TEXT);
        JComponent ed = s.getEditor();
        if (ed instanceof JSpinner.DefaultEditor de) {
            JTextField tf = de.getTextField();
            tf.setBackground(C_INPUT_BG); tf.setForeground(C_TEXT);
            tf.setFont(new Font(FN, Font.PLAIN, F_MD));
            tf.setCaretColor(C_ACCENT);
            tf.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }
    }

    private void styleProgressBar(JProgressBar pb, Color fg) {
        pb.setBackground(C_INPUT_BG); pb.setForeground(fg);
        pb.setFont(new Font(FN, Font.BOLD, F_SM));
        pb.setBorder(BorderFactory.createLineBorder(C_FIELD_BD));
    }

    private void styleTable(JTable t) {
        t.setBackground(C_INPUT_BG); t.setForeground(C_TEXT);
        t.setFont(new Font(FN, Font.PLAIN, F_MD));
        t.setGridColor(C_BORDER); t.setRowHeight(22);
        t.setSelectionBackground(new Color(0x00, 0x50, 0x80));
        t.setSelectionForeground(Color.WHITE);
        t.getTableHeader().setBackground(C_CARD);
        t.getTableHeader().setForeground(C_ACCENT);
        t.getTableHeader().setFont(new Font(FN, Font.BOLD, F_MD));
        t.getTableHeader().setBorder(BorderFactory.createLineBorder(C_BORDER));
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable tbl, Object val,
                    boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(tbl, val, sel, foc, r, c);
                setBackground(sel ? new Color(0x00, 0x50, 0x80) : C_INPUT_BG);
                setForeground(C_TEXT);
                if (c == 1 && val != null) {
                    String sv = val.toString();
                    if      (sv.contains("완료")) setForeground(C_SUCCESS);
                    else if (sv.contains("진행")) setForeground(C_ACCENT);
                    else if (sv.contains("오류")) setForeground(C_ERROR);
                    else if (sv.contains("대기")) setForeground(C_TEXT_DIM);
                }
                return this;
            }
        });
    }

    private void showInfo(String title, String msg) {
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String title, String msg) {
        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.ERROR_MESSAGE);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  main
    // ═════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            Color bg    = new Color(0x0F, 0x17, 0x23);
            Color panel = new Color(0x16, 0x21, 0x2E);
            Color card  = new Color(0x1C, 0x2A, 0x3A);
            Color text  = new Color(0xD0, 0xE8, 0xFF);
            Color thumb = new Color(0x2A, 0x3F, 0x55);

            UIManager.put("Panel.background",              bg);
            UIManager.put("OptionPane.background",         panel);
            UIManager.put("OptionPane.messageForeground",  text);
            UIManager.put("Button.background",             card);
            UIManager.put("Button.foreground",             text);
            UIManager.put("ScrollBar.background",          bg);
            UIManager.put("ScrollBar.thumb",               thumb);
            UIManager.put("SplitPane.background",          bg);
            UIManager.put("SplitPaneDivider.background",   thumb);
            UIManager.put("TabbedPane.selected",           card);
            UIManager.put("TabbedPane.background",         bg);
            UIManager.put("TabbedPane.foreground",         text);
            UIManager.put("TabbedPane.unselectedBackground", panel);
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new OracleDataTransfer().setVisible(true));
    }
}
