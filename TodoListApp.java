import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * TodoListApp.java
 *
 * Single-file Java Swing To‑Do List application.
 * Features:
 * - Add / edit / delete tasks
 * - Mark complete / incomplete (editable checkbox in table)
 * - Columns: Title, Due Date (yyyy-MM-dd), Priority (Low/Medium/High), Done
 * - Simple persistence to tasks.dat via Java serialization
 * - Keyboard shortcuts: Enter to add, Delete to remove selected, Ctrl+S to save
 * - Auto-saves on window close
 *
 * Requires: Java 8+
 * Run:
 *   javac TodoListApp.java
 *   java TodoListApp
 */
public class TodoListApp extends JFrame {
    private final TaskTableModel tableModel = new TaskTableModel();
    private final JTable table = new JTable(tableModel);

    private final JTextField titleField = new JTextField(20);
    private final JTextField dueField = new JTextField(10); // yyyy-MM-dd
    private final JComboBox<String> priorityBox = new JComboBox<>(new String[]{"Low", "Medium", "High"});
    private final JButton addButton = new JButton("Add");
    private final JButton editButton = new JButton("Edit");
    private final JButton deleteButton = new JButton("Delete");
    private final JButton saveButton = new JButton("Save");

    private static final String DATA_FILE = "tasks.dat";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public TodoListApp() {
        super("To‑Do List");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(800, 500);
        setLocationRelativeTo(null);

        setupLayout();
        attachListeners();

        loadTasks();
    }

    private void setupLayout() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Title:"));
        top.add(titleField);
        top.add(new JLabel("Due (yyyy-MM-dd):"));
        top.add(dueField);
        top.add(new JLabel("Priority:"));
        top.add(priorityBox);
        top.add(addButton);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(editButton);
        controls.add(deleteButton);
        controls.add(saveButton);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(3).setMaxWidth(80); // Done checkbox

        JScrollPane scroll = new JScrollPane(table);

        Container c = getContentPane();
        c.setLayout(new BorderLayout(8, 8));
        c.add(top, BorderLayout.NORTH);
        c.add(scroll, BorderLayout.CENTER);
        c.add(controls, BorderLayout.SOUTH);
    }

    private void attachListeners() {
        // Add task on button or Enter in title
        addButton.addActionListener(e -> addTaskFromInputs());
        titleField.addActionListener(e -> addTaskFromInputs());
        dueField.addActionListener(e -> addTaskFromInputs());

        // Edit selected
        editButton.addActionListener(e -> editSelectedTask());

        // Delete selected
        deleteButton.addActionListener(e -> deleteSelectedTask());

        // Save
        saveButton.addActionListener(e -> saveTasks());

        // Keyboard shortcuts
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) deleteSelectedTask();
            }
        });

        // Ctrl+S to save
        KeyStroke saveKs = KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(saveKs, "save");
        getRootPane().getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveTasks();
            }
        });

        // Double-click to edit
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editSelectedTask();
                }
            }
        });

        // Save on close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveTasks();
            }
        });
    }

    private void addTaskFromInputs() {
        String title = titleField.getText().trim();
        String dueText = dueField.getText().trim();
        String priority = (String) priorityBox.getSelectedItem();

        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a title.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate dueDate = null;
        if (!dueText.isEmpty()) {
            try {
                dueDate = LocalDate.parse(dueText, DATE_FMT);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Due date must be in yyyy-MM-dd format.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        Task t = new Task(title, dueDate, priority, false);
        tableModel.addTask(t);

        // clear inputs
        titleField.setText("");
        dueField.setText("");
        priorityBox.setSelectedIndex(1);
        titleField.requestFocusInWindow();
    }

    private void editSelectedTask() {
        int r = table.getSelectedRow();
        if (r == -1) {
            JOptionPane.showMessageDialog(this, "Select a task to edit.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = table.convertRowIndexToModel(r);
        Task t = tableModel.getTaskAt(modelRow);

        JTextField title = new JTextField(t.title);
        JTextField due = new JTextField(t.dueDate == null ? "" : t.dueDate.format(DATE_FMT));
        JComboBox<String> pr = new JComboBox<>(new String[]{"Low", "Medium", "High"});
        pr.setSelectedItem(t.priority);
        JCheckBox done = new JCheckBox("Done", t.done);

        JPanel p = new JPanel(new GridLayout(0, 1));
        p.add(new JLabel("Title:")); p.add(title);
        p.add(new JLabel("Due (yyyy-MM-dd):")); p.add(due);
        p.add(new JLabel("Priority:")); p.add(pr);
        p.add(done);

        int ok = JOptionPane.showConfirmDialog(this, p, "Edit Task", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            String newTitle = title.getText().trim();
            if (newTitle.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Title cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            LocalDate newDue = null;
            String dueText = due.getText().trim();
            if (!dueText.isEmpty()) {
                try { newDue = LocalDate.parse(dueText, DATE_FMT); } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Due date must be in yyyy-MM-dd format.", "Validation", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            t.title = newTitle;
            t.dueDate = newDue;
            t.priority = (String) pr.getSelectedItem();
            t.done = done.isSelected();
            tableModel.fireTableRowsUpdated(modelRow, modelRow);
        }
    }

    private void deleteSelectedTask() {
        int r = table.getSelectedRow();
        if (r == -1) return;
        int modelRow = table.convertRowIndexToModel(r);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete selected task?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            tableModel.removeTaskAt(modelRow);
        }
    }

    private void saveTasks() {
        List<Task> list = tableModel.getTasks();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(new ArrayList<>(list));
            oos.flush();
            // Small feedback
            setStatus("Saved " + list.size() + " tasks.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save tasks: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadTasks() {
        File f = new File(DATA_FILE);
        if (!f.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            Object o = ois.readObject();
            if (o instanceof List) {
                List<Task> loaded = (List<Task>) o;
                tableModel.setTasks(loaded);
                setStatus("Loaded " + loaded.size() + " tasks.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load tasks: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setStatus(String msg) {
        setTitle("To‑Do List — " + msg);
        // clear the message after a moment
        javax.swing.Timer t = new javax.swing.Timer(2500, e -> setTitle("To‑Do List"));
        t.setRepeats(false);
        t.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TodoListApp app = new TodoListApp();
            app.setVisible(true);
        });
    }

    // --- inner classes ---
    private static class Task implements Serializable {
        String title;
        LocalDate dueDate;
        String priority;
        boolean done;

        Task(String title, LocalDate dueDate, String priority, boolean done) {
            this.title = title;
            this.dueDate = dueDate;
            this.priority = priority;
            this.done = done;
        }
    }

    private static class TaskTableModel extends AbstractTableModel {
        private final String[] cols = {"Title", "Due", "Priority", "Done"};
        private final List<Task> tasks = new ArrayList<>();

        public void addTask(Task t) {
            tasks.add(0, t); // newest on top
            fireTableRowsInserted(0, 0);
        }

        public void removeTaskAt(int idx) {
            tasks.remove(idx);
            fireTableRowsDeleted(idx, idx);
        }

        public Task getTaskAt(int idx) {
            return tasks.get(idx);
        }

        public List<Task> getTasks() {
            return tasks;
        }

        public void setTasks(List<Task> list) {
            tasks.clear();
            tasks.addAll(list);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return tasks.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 3) return Boolean.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 3; // only Done checkbox editable directly
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Task t = tasks.get(rowIndex);
            switch (columnIndex) {
                case 0: return t.title;
                case 1: return t.dueDate == null ? "" : t.dueDate.format(DATE_FMT);
                case 2: return t.priority;
                case 3: return t.done;
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            Task t = tasks.get(rowIndex);
            if (columnIndex == 3 && aValue instanceof Boolean) {
                t.done = (Boolean) aValue;
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}
