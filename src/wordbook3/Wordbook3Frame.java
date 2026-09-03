package wordbook3;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.SQLException;
import java.util.List;

/** wordbook3 的 Swing 主窗口。 */
public final class Wordbook3Frame extends JFrame {
    private static final String[] TABLE_COLUMNS = {"ID", "单词", "释义", "例句", "发音文件", "创建时间"};

    private final WordRepository repository;
    private final AudioPlayer audioPlayer;
    private final JTextField searchField = new JTextField(18);
    private final JComboBox<SearchMode> searchModeBox = new JComboBox<>(SearchMode.values());
    private final JTextField wordField = new JTextField(18);
    private final JTextField meaningField = new JTextField(18);
    private final JTextArea sentenceArea = new JTextArea(3, 18);
    private final JTextField voiceField = new JTextField(18);
    private final DefaultTableModel tableModel = new DefaultTableModel(TABLE_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable wordTable = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel("准备就绪");
    private Long selectedId;

    public Wordbook3Frame(WordRepository repository, AudioPlayer audioPlayer) {
        super("英语单词簿");
        this.repository = repository;
        this.audioPlayer = audioPlayer;
        initializeWindow();
        installEventHandlers();
        refreshAll();
    }

    private void initializeWindow() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(920, 620));

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(createSearchPanel(), BorderLayout.NORTH);
        root.add(createContentPanel(), BorderLayout.CENTER);
        root.add(createBottomPanel(), BorderLayout.SOUTH);
        setContentPane(root);

        wordTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        wordTable.setAutoCreateRowSorter(true);
        wordTable.getColumnModel().getColumn(0).setMinWidth(0);
        wordTable.getColumnModel().getColumn(0).setMaxWidth(0);
        wordTable.getColumnModel().getColumn(0).setPreferredWidth(0);
        sentenceArea.setLineWrap(true);
        sentenceArea.setWrapStyleWord(true);
        setSize(1060, 680);
        setLocationRelativeTo(null);
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setBorder(BorderFactory.createTitledBorder("单词查询"));
        panel.add(new JLabel("关键字"));
        panel.add(searchField);
        panel.add(searchModeBox);
        panel.add(button("查询", this::refreshSearch));
        panel.add(button("显示全部", this::refreshAll));
        return panel;
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JScrollPane tableScrollPane = new JScrollPane(wordTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("查询结果"));
        panel.add(tableScrollPane, BorderLayout.CENTER);
        panel.add(createEditorPanel(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createEditorPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("单词详情"));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;

        addField(panel, constraints, 0, 0, "单词", wordField);
        addField(panel, constraints, 2, 0, "中文释义", meaningField);
        addField(panel, constraints, 0, 1, "发音文件名", voiceField);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("例句"), constraints);
        constraints.gridx = 1;
        constraints.gridwidth = 3;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(sentenceArea), constraints);
        return panel;
    }

    private void addField(
            JPanel panel,
            GridBagConstraints constraints,
            int x,
            int y,
            String label,
            JTextField field
    ) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), constraints);
        constraints.gridx = x + 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, constraints);
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(button("新增", this::runCreate));
        buttons.add(button("修改", this::runUpdate));
        buttons.add(button("删除", this::runDelete));
        buttons.add(button("清空", this::clearEditor));
        buttons.add(button("播放发音", this::playVoice));
        panel.add(buttons, BorderLayout.WEST);
        panel.add(statusLabel, BorderLayout.CENTER);
        return panel;
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void installEventHandlers() {
        wordTable.getSelectionModel().addListSelectionListener(this::fillEditorFromSelection);
    }

    private void fillEditorFromSelection(ListSelectionEvent event) {
        if (event.getValueIsAdjusting() || wordTable.getSelectedRow() < 0) {
            return;
        }
        int row = wordTable.convertRowIndexToModel(wordTable.getSelectedRow());
        selectedId = ((Number) tableModel.getValueAt(row, 0)).longValue();
        wordField.setText(String.valueOf(tableModel.getValueAt(row, 1)));
        meaningField.setText(String.valueOf(tableModel.getValueAt(row, 2)));
        sentenceArea.setText(String.valueOf(tableModel.getValueAt(row, 3)));
        voiceField.setText(String.valueOf(tableModel.getValueAt(row, 4)));
        showStatus("已选择：" + wordField.getText());
    }

    private void refreshAll() {
        try {
            refreshTable(repository.findAll());
            showStatus("已加载全部单词");
        } catch (SQLException exception) {
            showError("读取单词失败：" + exception.getMessage());
        }
    }

    private void refreshSearch() {
        try {
            List<Word> words = repository.search(
                    searchField.getText(),
                    (SearchMode) searchModeBox.getSelectedItem()
            );
            refreshTable(words);
            showStatus("查询到 " + words.size() + " 条记录");
        } catch (SQLException | IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void refreshTable(List<Word> words) {
        tableModel.setRowCount(0);
        selectedId = null;
        for (Word word : words) {
            tableModel.addRow(new Object[]{
                    word.id(), word.word(), word.meaning(), word.sentence(), word.voice(), word.createdAt()
            });
        }
        wordTable.clearSelection();
    }

    private void runCreate() {
        try {
            repository.insert(wordField.getText(), meaningField.getText(), sentenceArea.getText(), voiceField.getText());
            completeMutation("新增成功");
        } catch (SQLException | IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void runUpdate() {
        if (selectedId == null) {
            showError("请选择要修改的单词");
            return;
        }
        try {
            repository.update(selectedId, wordField.getText(), meaningField.getText(), sentenceArea.getText(), voiceField.getText());
            completeMutation("修改成功");
        } catch (SQLException | IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void runDelete() {
        if (selectedId == null) {
            showError("请选择要删除的单词");
            return;
        }
        int option = JOptionPane.showConfirmDialog(
                this,
                "确认删除单词“" + wordField.getText() + "”吗？",
                "确认删除",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (option != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            repository.delete(selectedId);
            completeMutation("删除成功");
        } catch (SQLException | IllegalArgumentException exception) {
            showError(exception.getMessage());
        }
    }

    private void clearEditor() {
        wordTable.clearSelection();
        resetEditorFields();
        showStatus("编辑区已清空");
    }

    private void playVoice() {
        boolean started = audioPlayer.playAsync(
                voiceField.getText(),
                message -> SwingUtilities.invokeLater(() -> showError(message))
        );
        if (started) {
            showStatus("正在播放：" + voiceField.getText());
        }
    }

    private void completeMutation(String message) {
        refreshAll();
        resetEditorFields();
        showStatus(message);
    }

    private void resetEditorFields() {
        selectedId = null;
        wordField.setText("");
        meaningField.setText("");
        sentenceArea.setText("");
        voiceField.setText("");
    }

    private void showError(String message) {
        String safeMessage = message == null ? "未知错误" : message;
        JOptionPane.showMessageDialog(this, safeMessage, "提示", JOptionPane.ERROR_MESSAGE);
        showStatus(safeMessage);
    }

    private void showStatus(String message) {
        statusLabel.setText(message);
    }
}
