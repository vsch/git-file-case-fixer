/*
 * MIT License
 *
 * Copyright (c) 2018, Vladimir Schneider, vladimir.schneider@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.vladsch.git.filecase.fixer;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.editors.JBComboBoxTableCellEditorComponent;
import com.intellij.ui.table.TableView;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.ui.*;
import com.vladsch.git.filecase.fixer.GitFileFixerProjectRoots.GitRepoFile;
import icons.PluginIcons;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.vladsch.git.filecase.fixer.GitFixerConfiguration.FIX_PROMPT;

public class GitFileCaseShowMismatches {
    JPanel myMainPanel;
    ListTableModel<GitRepoFile> myRepoFilesModel;
    TableView<GitRepoFile> myRepoFilesTable;
    final Runnable myUpdateRunnable;
    @Nullable HashMap<GitRepoFile, String> myDirectoryMismatches = null;

    static Color getInvalidTextFieldBackground() {
        return Utils.errorColor(UIUtil.getTextFieldBackground());
    }

    static Color getValidTextFieldBackground() {
        return UIUtil.getTextFieldBackground();
    }

    //Color getSelectedTextFieldBackground() {
    //    return myTextSample.getSelectionColor();
    //}

    static Color getInvalidTableBackground(boolean isSelected) {
        return Utils.errorColor(UIUtil.getTableBackground(isSelected));
    }

    static Color getTableBackground(boolean isSelected) {
        return UIUtil.getTableBackground(isSelected);
    }

    //public JComponent getComponent() {
    //    return myMainPanel;
    //}

    public List<GitRepoFile> getRepoFileList() {
        return myRepoFilesModel.getItems();
    }

    public GitFileCaseShowMismatches() {
        this(null);
    }

    public GitFileCaseShowMismatches(final Runnable updateRunnable) {
        myUpdateRunnable = updateRunnable;

        if (myUpdateRunnable != null) {
            myRepoFilesModel.addTableModelListener(e -> {
                myUpdateRunnable.run();
            });
        }
    }

    /**
     * Shortest case mismatch path which is shorter than full path
     * @param file   git repo file
     * @return String shortest case mismatch path or null
     */
    static @Nullable
    String shortestCaseMismatchPath(GitRepoFile file) {
        String[] parts1 = file.gitPath.split("/");
        String[] parts2 = file.filePath.split("/");
        int iMax = parts1.length;
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (int i = 0; i < iMax - 1; i++) {
            sb.append(sep).append(parts1[i].toLowerCase());
            sep = "/";

            if (!parts1[i].equals(parts2[i])) {
                return sb.toString();
            }
        }
        return null;
    }

    public void setRepoFileList(List<GitRepoFile> repoFileList) {
        // need to create a list of directory mismatches if the difference is in the path
        myDirectoryMismatches = null;

        for (GitRepoFile file : repoFileList) {
            String mismatchPath = shortestCaseMismatchPath(file);
            if (mismatchPath != null) {
                if (myDirectoryMismatches == null) myDirectoryMismatches = new HashMap<>();
                myDirectoryMismatches.put(file, mismatchPath);
            }
        }
        myRepoFilesModel.setItems(repoFileList);
    }

    private void createUIComponents() {
        GridConstraints constraints = new GridConstraints(0, 0, 1, 1
                , GridConstraints.ANCHOR_CENTER
                , GridConstraints.FILL_BOTH
                , GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK
                , GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK
                , null, null, null);

        ColumnInfo[] linkTextColumns = { new GitPathColumn(), new FixColumn(this), new FilePathColumn() };
        myRepoFilesModel = new ListTableModel<>(linkTextColumns, new ArrayList<>(), 0);
        myRepoFilesTable = new TableView<>(myRepoFilesModel);
        myRepoFilesTable.setPreferredScrollableViewportSize(JBUI.size(-1, -1));
        myRepoFilesTable.setRowSelectionAllowed(true);

        myMainPanel = new JPanel(new BorderLayout());
        myRepoFilesTable.setFillsViewportHeight(true);
        JBScrollPane jbScrollPane = new JBScrollPane(myRepoFilesTable, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        myMainPanel.add(jbScrollPane, BorderLayout.CENTER);
    }

    final static String[] fixChoices = {
            Bundle.message("show.mismatches.table-column.fix.choice.ask"),
            Bundle.message("show.mismatches.table-column.fix.choice.git"),
            Bundle.message("show.mismatches.table-column.fix.choice.file-system"),
    };

    final static Icon[] fixColumnIcons = {
            PluginIcons.NoArrow,
            PluginIcons.LeftArrow,
            PluginIcons.RightArrow,
    };

    void updateFixAction(GitRepoFile repoFile, int action) {
        String mismatchPath = myDirectoryMismatches != null ? myDirectoryMismatches.get(repoFile) : null;
        if (mismatchPath != null) {
            boolean hadChanges = false;
            int iMax = myRepoFilesModel.getRowCount();
            for (int i = 0; i < iMax; i++) {
                GitRepoFile gitRepoFile = myRepoFilesModel.getItem(i);
                String gitMismatchPath = myDirectoryMismatches.get(gitRepoFile);
                if (mismatchPath.equals(gitMismatchPath)) {
                    gitRepoFile.fixAction = action;
                    hadChanges = true;
                }
            }

            if (hadChanges) {
                myRepoFilesModel.fireTableDataChanged();
            }
        }
    }

    static class FixColumn extends MyColumnInfo<String> {
        public static final int ROW_HEIGHT_OFFSET = 2;
        final GitFileCaseShowMismatches myMismatches;

        public FixColumn(final GitFileCaseShowMismatches mismatches) {
            super(Bundle.message("show.mismatches.table-column.fix"));
            myMismatches = mismatches;
        }

        public TableCellRenderer getRenderer(final GitRepoFile gitRepoFile) {
            int action = gitRepoFile.fixAction;

            return new DefaultTableCellRenderer() {
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    final DefaultTableCellRenderer rendererComponent = (DefaultTableCellRenderer) super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
                    rendererComponent.setHorizontalAlignment(SwingConstants.CENTER);
                    setText("");
                    setIcon(fixColumnIcons[action]);
                    //if (table.getRowHeight() != myTextSample.getPreferredSize().height + ROW_HEIGHT_OFFSET) table.setRowHeight(myTextSample.getPreferredSize().height + ROW_HEIGHT_OFFSET);
                    return rendererComponent;
                }

                @Override
                public String getToolTipText() {
                    return super.getToolTipText();
                }

                @Override
                protected void setValue(Object value) {
                    super.setValue(value);
                }

                @Override
                public Icon getIcon() {
                    return fixColumnIcons[action];
                }
            };
        }

        public TableCellEditor getEditor(final GitRepoFile gitRepoFile) {
            return new AbstractTableCellEditor() {
                private final JBComboBoxTableCellEditorComponent myFixChooser = new JBComboBoxTableCellEditorComponent();

                public Object getCellEditorValue() {
                    return myFixChooser.getEditorValue();
                }

                public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                    myFixChooser.setCell(table, row, column);
                    myFixChooser.setOptions((Object[]) fixChoices);
                    myFixChooser.setDefaultValue(value);
                    myFixChooser.setToString(o -> (String) o);
                    myFixChooser.addActionListener(e -> {
                        // if the case diff is in the parent need to change all files in the parent to the same fix type
                        int action = getAction((String) myFixChooser.getEditorValue());
                        if (action != FIX_PROMPT) {
                            myMismatches.updateFixAction(gitRepoFile, action);
                        }
                    });
                    return myFixChooser;
                }
            };
        }

        public String valueOf(final GitRepoFile object) {
            return fixChoices[object.fixAction];
        }

        public void setValue(final GitRepoFile item, final String choice) {
            if (item != null) {
                item.fixAction = getAction(choice);
            }
        }

        static int getAction(final String choice) {
            return fixChoices[GitFixerConfiguration.FIX_FILE_SYSTEM].equals(choice) ? GitFixerConfiguration.FIX_FILE_SYSTEM : fixChoices[GitFixerConfiguration.FIX_GIT].equals(choice) ? GitFixerConfiguration.FIX_GIT : FIX_PROMPT;
        }

        @Override
        public boolean isCellEditable(final GitRepoFile item) {
            return true;
        }

        @Override
        public int getWidth(JTable table) {
            return 30;
        }
    }

    static class GitPathColumn extends MyColumnInfo<String> {
        GitPathColumn() {
            super(Bundle.message("show.mismatches.table-column.git-path"));
        }

        public TableCellRenderer getRenderer(final GitRepoFile gitRepoFile) {
            return new MyDefaultTableCellRenderer(gitRepoFile);
        }

        public String valueOf(final GitRepoFile object) {
            return object.gitPath;
        }
    }

    static class FilePathColumn extends MyColumnInfo<String> {
        FilePathColumn() {
            super(Bundle.message("show.mismatches.table-column.file-path"));
        }

        public TableCellRenderer getRenderer(final GitRepoFile gitRepoFile) {
            return new MyDefaultTableCellRenderer(gitRepoFile);
        }

        public String valueOf(final GitRepoFile object) {
            return object.filePath;
        }
    }

    private static class MyDefaultTableCellRenderer extends JTextField implements TableCellRenderer {
        final GitRepoFile myRepoFile;
        public DefaultHighlighter high = new DefaultHighlighter();

        public DefaultHighlighter.DefaultHighlightPainter highlight_painter = new DefaultHighlightPainter(new JBColor(new Color(239, 175, 255),
                new Color(163, 2, 68)));

        public MyDefaultTableCellRenderer(final GitRepoFile repoFile) {
            setBorder(BorderFactory.createEmptyBorder());
            setHighlighter(high);
            myRepoFile = repoFile;
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            //final Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setFont(table.getFont());
            String text = (String) value;

            setText(" " + text);

            int lastPos = -1;
            int iMax = text.length();
            for (int i = 0; i < iMax; i++) {
                if (myRepoFile.filePath.charAt(i) != myRepoFile.gitPath.charAt(i)) {
                    if (lastPos == -1) {
                        lastPos = i;
                    }
                } else {
                    if (lastPos != -1) {
                        try {
                            high.addHighlight(lastPos + 1, i + 1, highlight_painter);
                        } catch (BadLocationException e) {
                            e.printStackTrace();
                        }
                        lastPos = -1;
                    }
                }
            }

            if (lastPos != -1) {
                try {
                    high.addHighlight(lastPos + 1, iMax + 1, highlight_painter);
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }
            return this;
        }

        @Override
        public String getToolTipText() {
            return super.getToolTipText();
        }
    }

    private abstract static class MyColumnInfo<T> extends ColumnInfo<GitRepoFile, T> {
        protected Color myInvalidBackground = getInvalidTableBackground(false);
        protected Color myInvalidSelectedBackground = getInvalidTableBackground(true);

        protected MyColumnInfo(final String name) {
            super(name);
        }

        @Override
        public boolean isCellEditable(final GitRepoFile item) {
            return false;
        }
    }
}
