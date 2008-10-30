package documentclassifier;

import documentclassifier.Metriche.TFIDFComparator;
import documentclassifier.Metriche.BhattacharryaDistanceComparator;
import java.awt.event.KeyEvent;
import documentclassifier.DocumentClassifierApp;
import java.util.concurrent.ExecutionException;
import java.util.prefs.PreferenceChangeEvent;
import org.jdesktop.application.Action;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.Task;
import org.jdesktop.application.TaskMonitor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.lang.Integer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.swing.Timer;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.io.IOException;
import java.lang.Double;
import java.lang.Object;
import java.lang.String;
import java.net.URLConnection;
import java.util.AbstractMap.SimpleEntry;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ResourceBundle;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.prefs.PreferenceChangeListener;
import javax.net.ssl.HttpsURLConnection;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DefaultCaret;
import org.jdesktop.application.Application;

/**
 * This class represents the main frame of the application.
 * 
 * @author      Salvo Danilo Giuffrida (giuffsalvo@hotmail.it, salvodanilogiuffrida@gmail.com)
 */
public class DocumentClassifierView extends FrameView implements PreferenceChangeListener {

    /**
     * The application this JFrame refers to.
     */
    private DocumentClassifierApp application;
    /**
     * Resource bundle for this class' localized resources.
     */
    private ResourceBundle documentClassifierViewResources=ResourceBundle.getBundle("documentclassifier/resources/DocumentClassifierView");
    /**
     * This variable mantains the reference to the current query document.
     */
    private Document currentQuery;
    /**
     * This is a list of maps 'String'->'Object', each one containing information on a document of the
     * training set, and whose content is used when we want to visualize such information on the appropriate
     * JTable after the ranking phase.
     */
    private LinkedList<Map<String, Object>> infoDocuments;
    /**
     * Each entry in the list aforementioned is made of a vector of objects containing information on a document
     * of the training set (Bhattacharrya distance/Cosine of the angle with the query document, Category, Title),
     * and a map that represents the document's histogram.
     */
    private PreferencesDialog preferencesDialog;
    private Task currentTask;
    private DefaultTableModel tableModel;

    public DocumentClassifierView(SingleFrameApplication app) {

        super(app);
        initComponents();
        
        infoDocuments = new LinkedList<Map<String, Object>>();
        this.getFrame().setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        /**
         * The input map associated with the JTable that visualizes the list of documents ranked to the current query is
         * modified, so that when the user presses the key 'Enter' on an entry of the JTable, instead of the default behavior
         * (scrolling to the next entry), a personalized one can be implemented.
         */
        listDocuments.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
        /**
         * I set the property 'UpdatePolicy' of the caret associated to the {@link JTextArea} 'textAreaOutput', which contains the status
         * messages during the execution of the various tasks (scraping of the query document, classification, validation)
         */
        ((DefaultCaret) textAreaOutput.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        application = (DocumentClassifierApp) app;
        tableModel = (DefaultTableModel) listDocuments.getModel();

        /**
         * An event of type 'PreferenceChangeEvent' is forcefully generated (this class is a registered listener of it),
         * to check what it the current chosen metric, and eventually change the label on the first column of the table,
         * from "Cosine" to "Distance", in case the chosen metric is the Bhattacharrya distance
         */
        preferenceChange(new PreferenceChangeEvent(application.getPreferences(), MapDefaultPreferences.METRIC, application.getMetric(false)));

        // Do NOT touch the following section!
        // status bar initialization - message timeout, idle icon and busy animation, etc
        ResourceMap resourceMap = getResourceMap();
        int messageTimeout = resourceMap.getInteger("StatusBar.messageTimeout");
        messageTimer = new Timer(messageTimeout, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                statusMessageLabel.setText("");
            }
        });
        messageTimer.setRepeats(false);
        int busyAnimationRate = resourceMap.getInteger("StatusBar.busyAnimationRate");
        for (int i = 0; i < busyIcons.length; i++) {
            busyIcons[i] = resourceMap.getIcon("StatusBar.busyIcons[" + i + "]");
        }
        busyIconTimer = new Timer(busyAnimationRate, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                busyIconIndex = (busyIconIndex + 1) % busyIcons.length;
                statusAnimationLabel.setIcon(busyIcons[busyIconIndex]);
            }
        });
        idleIcon = resourceMap.getIcon("StatusBar.idleIcon");
        statusAnimationLabel.setIcon(idleIcon);
        progressBar.setVisible(false);

        // connecting action tasks to status bar via TaskMonitor
        TaskMonitor taskMonitor = new TaskMonitor(getApplication().getContext());
        taskMonitor.addPropertyChangeListener(new java.beans.PropertyChangeListener() {

            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();
                if ("started".equals(propertyName)) {
                    if (!busyIconTimer.isRunning()) {
                        statusAnimationLabel.setIcon(busyIcons[0]);
                        busyIconIndex = 0;
                        busyIconTimer.start();
                    }
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(true);
                } else if ("done".equals(propertyName)) {
                    busyIconTimer.stop();
                    statusAnimationLabel.setIcon(idleIcon);
                    progressBar.setVisible(false);
                    progressBar.setValue(0);
                } else if ("message".equals(propertyName)) {
                    String text = (String) (evt.getNewValue());
                    statusMessageLabel.setText((text == null) ? "" : text);
                //messageTimer.restart();
                } else if ("progress".equals(propertyName)) {
                    int value = (Integer) (evt.getNewValue());
                    progressBar.setVisible(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(value);
                }
            }
        });
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        fieldURL = new javax.swing.JTextField();
        buttonRead = new javax.swing.JButton();
        scrollPanelLabelTitle = new javax.swing.JScrollPane();
        labelTitle = new javax.swing.JLabel();
        buttonStop = new javax.swing.JButton();
        buttonFind = new javax.swing.JButton();
        toolbar = new javax.swing.JToolBar();
        separatorInitial = new javax.swing.JToolBar.Separator();
        buttonOpen = new javax.swing.JButton();
        separatorOpenTraining = new javax.swing.JToolBar.Separator();
        buttonValidation = new javax.swing.JButton();
        splitPaneVertical = new javax.swing.JSplitPane();
        splitPaneHorizontal = new javax.swing.JSplitPane();
        scrollPaneRankedDocuments = new javax.swing.JScrollPane();
        listDocuments = new javax.swing.JTable();
        scrollPaneLabelText = new javax.swing.JScrollPane();
        labelText = new javax.swing.JTextArea();
        scrollPaneTextAreaOutput = new javax.swing.JScrollPane();
        textAreaOutput = new javax.swing.JTextArea();
        menuBar = new javax.swing.JMenuBar();
        javax.swing.JMenu fileMenu = new javax.swing.JMenu();
        openMenuItem = new javax.swing.JMenuItem();
        trainingMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenuItem esciMenuItem = new javax.swing.JMenuItem();
        modifyMenu = new javax.swing.JMenu();
        menuItemPreferenze = new javax.swing.JMenuItem();
        javax.swing.JMenu helpMenu = new javax.swing.JMenu();
        helpMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();
        statusPanel = new javax.swing.JPanel();
        javax.swing.JSeparator statusPanelSeparator = new javax.swing.JSeparator();
        statusMessageLabel = new javax.swing.JLabel();
        statusAnimationLabel = new javax.swing.JLabel();
        progressBar = new javax.swing.JProgressBar();
        metricsGroup = new javax.swing.ButtonGroup();

        mainPanel.setName("mainPanel"); // NOI18N

        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(documentclassifier.DocumentClassifierApp.class).getContext().getResourceMap(DocumentClassifierView.class);
        fieldURL.setToolTipText(resourceMap.getString("fieldURL.toolTipText")); // NOI18N
        fieldURL.setName("fieldURL"); // NOI18N
        fieldURL.addCaretListener(new javax.swing.event.CaretListener() {
            public void caretUpdate(javax.swing.event.CaretEvent evt) {
                fieldURLCaretUpdate(evt);
            }
        });

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(documentclassifier.DocumentClassifierApp.class).getContext().getActionMap(DocumentClassifierView.class, this);
        buttonRead.setAction(actionMap.get("readInputDocument")); // NOI18N
        buttonRead.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        buttonRead.setName("buttonRead"); // NOI18N

        scrollPanelLabelTitle.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scrollPanelLabelTitle.setFont(scrollPanelLabelTitle.getFont());
        scrollPanelLabelTitle.setName("scrollPanelLabelTitle"); // NOI18N

        labelTitle.setFont(new java.awt.Font("DejaVu Sans", 1, 12));
        labelTitle.setToolTipText(resourceMap.getString("labelTitle.toolTipText")); // NOI18N
        labelTitle.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        labelTitle.setName("labelTitle"); // NOI18N
        scrollPanelLabelTitle.setViewportView(labelTitle);

        buttonStop.setAction(actionMap.get("stopCurrentTask")); // NOI18N
        buttonStop.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        buttonStop.setName("buttonStop"); // NOI18N

        buttonFind.setAction(actionMap.get("rankDocuments")); // NOI18N
        buttonFind.setName("buttonFind"); // NOI18N

        toolbar.setFloatable(false);
        toolbar.setRollover(true);
        toolbar.setName("toolbar"); // NOI18N

        separatorInitial.setName("separatorInitial"); // NOI18N
        toolbar.add(separatorInitial);

        buttonOpen.setAction(actionMap.get("openDocument")); // NOI18N
        buttonOpen.setFocusable(false);
        buttonOpen.setName("buttonOpen"); // NOI18N
        buttonOpen.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        toolbar.add(buttonOpen);

        separatorOpenTraining.setName("separatorOpenTraining"); // NOI18N
        toolbar.add(separatorOpenTraining);

        buttonValidation.setAction(actionMap.get("Validation")); // NOI18N
        buttonValidation.setFocusable(false);
        buttonValidation.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        buttonValidation.setName("buttonValidation"); // NOI18N
        buttonValidation.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        toolbar.add(buttonValidation);

        splitPaneVertical.setDividerLocation(420);
        splitPaneVertical.setDividerSize(10);
        splitPaneVertical.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        splitPaneVertical.setContinuousLayout(true);
        splitPaneVertical.setName("splitPaneVertical"); // NOI18N
        splitPaneVertical.setOneTouchExpandable(true);

        splitPaneHorizontal.setDividerLocation(360);
        splitPaneHorizontal.setDividerSize(10);
        splitPaneHorizontal.setContinuousLayout(true);
        splitPaneHorizontal.setName("splitPaneHorizontal"); // NOI18N
        splitPaneHorizontal.setOneTouchExpandable(true);

        scrollPaneRankedDocuments.setName("scrollPaneRankedDocuments"); // NOI18N

        listDocuments.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Cosine", "Categories", "Title"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.Object.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        listDocuments.setToolTipText(resourceMap.getString("listDocuments.toolTipText")); // NOI18N
        listDocuments.setColumnSelectionAllowed(true);
        listDocuments.setName("listDocuments"); // NOI18N
        listDocuments.getTableHeader().setReorderingAllowed(false);
        listDocuments.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                clickDocuments(evt);
            }
        });
        listDocuments.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                keyPressedDocuments(evt);
            }
        });
        scrollPaneRankedDocuments.setViewportView(listDocuments);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("documentclassifier/resources/DocumentClassifierView"); // NOI18N
        listDocuments.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        listDocuments.getColumnModel().getColumn(0).setHeaderValue(bundle.getString("listDocuments.title0.Cosine")); // NOI18N
        listDocuments.getColumnModel().getColumn(1).setHeaderValue(bundle.getString("listDocuments.title1")); // NOI18N
        listDocuments.getColumnModel().getColumn(2).setHeaderValue(bundle.getString("listDocuments.title2")); // NOI18N

        splitPaneHorizontal.setRightComponent(scrollPaneRankedDocuments);

        scrollPaneLabelText.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPaneLabelText.setName("scrollPaneLabelText"); // NOI18N

        labelText.setColumns(20);
        labelText.setEditable(false);
        labelText.setLineWrap(true);
        labelText.setRows(1);
        labelText.setToolTipText(resourceMap.getString("labelText.toolTipText")); // NOI18N
        labelText.setWrapStyleWord(true);
        labelText.setName("labelText"); // NOI18N
        scrollPaneLabelText.setViewportView(labelText);

        splitPaneHorizontal.setLeftComponent(scrollPaneLabelText);

        splitPaneVertical.setTopComponent(splitPaneHorizontal);

        scrollPaneTextAreaOutput.setName("scrollPaneTextAreaOutput"); // NOI18N

        textAreaOutput.setEditable(false);
        textAreaOutput.setLineWrap(true);
        textAreaOutput.setToolTipText(resourceMap.getString("textAreaOutput.toolTipText")); // NOI18N
        textAreaOutput.setWrapStyleWord(true);
        textAreaOutput.setName("textAreaOutput"); // NOI18N
        scrollPaneTextAreaOutput.setViewportView(textAreaOutput);

        splitPaneVertical.setRightComponent(scrollPaneTextAreaOutput);

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(toolbar, javax.swing.GroupLayout.DEFAULT_SIZE, 819, Short.MAX_VALUE)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(fieldURL, javax.swing.GroupLayout.DEFAULT_SIZE, 705, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(buttonRead, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(buttonStop, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scrollPanelLabelTitle, javax.swing.GroupLayout.DEFAULT_SIZE, 731, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(buttonFind)
                .addContainerGap())
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(splitPaneVertical)
                .addContainerGap())
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addComponent(toolbar, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(fieldURL, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(buttonStop, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(buttonRead))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(buttonFind)
                    .addComponent(scrollPanelLabelTitle, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(splitPaneVertical, javax.swing.GroupLayout.DEFAULT_SIZE, 491, Short.MAX_VALUE)
                .addContainerGap())
        );

        mainPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {buttonRead, buttonStop, fieldURL});

        menuBar.setName("menuBar"); // NOI18N

        fileMenu.setMnemonic('F');
        fileMenu.setText(resourceMap.getString("fileMenu.text")); // NOI18N
        fileMenu.setName("fileMenu"); // NOI18N

        openMenuItem.setAction(actionMap.get("openDocument")); // NOI18N
        openMenuItem.setText(resourceMap.getString("openMenuItem.text")); // NOI18N
        openMenuItem.setName("openMenuItem"); // NOI18N
        fileMenu.add(openMenuItem);

        trainingMenuItem.setAction(actionMap.get("Validation")); // NOI18N
        trainingMenuItem.setText(resourceMap.getString("validationMenuItem.text")); // NOI18N
        trainingMenuItem.setName("validationMenuItem"); // NOI18N
        fileMenu.add(trainingMenuItem);

        esciMenuItem.setAction(actionMap.get("quit")); // NOI18N
        esciMenuItem.setText(resourceMap.getString("exitMenuItem.text")); // NOI18N
        esciMenuItem.setName("exitMenuItem"); // NOI18N
        fileMenu.add(esciMenuItem);

        menuBar.add(fileMenu);

        modifyMenu.setMnemonic('M');
        modifyMenu.setText(resourceMap.getString("modifyMenu.text")); // NOI18N
        modifyMenu.setName("modifyMenu"); // NOI18N

        menuItemPreferenze.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_MASK));
        menuItemPreferenze.setText(resourceMap.getString("menuItemPreferences.text")); // NOI18N
        menuItemPreferenze.setName("menuItemPreferences"); // NOI18N
        menuItemPreferenze.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openPreferences(evt);
            }
        });
        modifyMenu.add(menuItemPreferenze);

        menuBar.add(modifyMenu);

        helpMenu.setText(resourceMap.getString("helpMenu.text")); // NOI18N
        helpMenu.setName("helpMenu"); // NOI18N

        helpMenuItem.setAction(actionMap.get("showHelp")); // NOI18N
        helpMenuItem.setText(resourceMap.getString("helpMenuItem.text")); // NOI18N
        helpMenuItem.setName("helpMenuItem"); // NOI18N
        helpMenu.add(helpMenuItem);

        aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
        aboutMenuItem.setName("aboutMenuItem"); // NOI18N
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        statusPanel.setName("statusPanel"); // NOI18N

        statusPanelSeparator.setName("statusPanelSeparator"); // NOI18N

        statusMessageLabel.setName("statusMessageLabel"); // NOI18N

        statusAnimationLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        statusAnimationLabel.setName("statusAnimationLabel"); // NOI18N

        progressBar.setName("progressBar"); // NOI18N

        javax.swing.GroupLayout statusPanelLayout = new javax.swing.GroupLayout(statusPanel);
        statusPanel.setLayout(statusPanelLayout);
        statusPanelLayout.setHorizontalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(statusPanelSeparator, javax.swing.GroupLayout.DEFAULT_SIZE, 819, Short.MAX_VALUE)
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(statusMessageLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 635, Short.MAX_VALUE)
                .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statusAnimationLabel)
                .addContainerGap())
        );
        statusPanelLayout.setVerticalGroup(
            statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(statusPanelLayout.createSequentialGroup()
                .addComponent(statusPanelSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(statusPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(statusMessageLabel)
                    .addComponent(statusAnimationLabel)
                    .addComponent(progressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(3, 3, 3))
        );

        setComponent(mainPanel);
        setMenuBar(menuBar);
        setStatusBar(statusPanel);
    }// </editor-fold>//GEN-END:initComponents
    private JFileChooser fileChooser;

    /**
     * Action associated with the menù entry File->Open: It shows a JFileChooser, used to choose
     * from the local filesystem a query document to classify.
     */
    @Action
    public void openDocument() {
        try {
            if (fileChooser == null) {
                fileChooser = new JFileChooser();
                fileChooser.setCurrentDirectory(new File("."));
            }
            if (fileChooser.showOpenDialog(this.getComponent()) == JFileChooser.APPROVE_OPTION) {
                fieldURL.setText(fileChooser.getSelectedFile().getCanonicalPath());
            }
        } catch (IOException ex) {
            showErrorMessage(ex.toString());
        }
    }

    /**
     * Method that opens a document (whose path is specified in input) in an external viewer.
     * 
     * @param   path        The path of the document that must be opened.
     * @throws  java.lang.Exception
     */
    private void openDocument(String path) throws Exception {

        //Desktop.getDesktop().browse(new URI(path));
        if (System.getProperty("os.name").toLowerCase().indexOf("windows") > -1) {
            Runtime.getRuntime().exec("cmd /c " + path);
        } else if (System.getProperty("os.name").toLowerCase().indexOf("mac") > -1) {
            Runtime.getRuntime().exec("open " + path);
        } else {
            Runtime.getRuntime().exec("firefox " + path);
        }
    }

    /**
     * When the user double clicks on one or more of the entries of the beforementioned list,
     * this method handles the opening of the corresponding documents in an external viewer.
     * 
     * @param  evt              The MouseEvent generated by the double click of the user.
     */
    private void clickDocuments(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_clickDocuments
        try {
            int numberDoubleClicks = evt.getClickCount() / 2;
            if (numberDoubleClicks >= 1) {
                for (int index : listDocuments.getSelectedRows()) {
                    String path = (String) infoDocuments.get(index).get("Path");
                    openDocument(path);
                }
            }
        } catch (Exception ex) {
            showErrorMessage(ex.toString());
        }
}//GEN-LAST:event_clickDocuments

    /**
     * This method handles the ActionEvent generated when the user clicks, or presses 'Enter', on the menù entry 'Modify->Preferences':
     * Its task is to show the panel for the configuration of the various program's preferences.
     * 
     * @param evt               The ActionEvent generated when the user clicks or presses 'Enter'.
     */
private void openPreferences(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openPreferences
    preferencesDialog = new PreferencesDialog(this, true);
    preferencesDialog.setVisible(true);
}//GEN-LAST:event_openPreferences

    /**
     * This method is called every time the caret of the JTextField containing the URL of the query document
     * changes its position.
     * 
     * @param evt               The {@link javax.swing.event.CaretEvent CaretEvent} generated every time the
     *                          caret of the JTextField containing the URL of the query document changes
     *                          its position (for example because of the insertion or deletion of characters
     *                          from it).
     */
private void fieldURLCaretUpdate(javax.swing.event.CaretEvent evt) {//GEN-FIRST:event_fieldURLCaretUpdate
    /**
     * Every time the position of the caret changes, the method controls if this event has been triggered by
     * the removal or the insertion of characters in the JTextField.
     * In case character have been removed, and the field has become empty, the property 'indirizzoValido',
     * which controls the enabling of the task for scraping the current query document ({@link LeggiDocumentoInputTask}),
     * is set to "false", otherwise to "true".
     */
    String URLText = fieldURL.getText();
    if (!URLText.isEmpty()) {
        setURLValid(true);
    } else {
        setURLValid(false);
    }
}//GEN-LAST:event_fieldURLCaretUpdate

    /**
     * This method is called every time that a button of the keyboard is pressed, while the GUI has its focus
     * on the list of documents of the training set ranked to the current query document.
     * 
     * @param evt               The KeyEvent generated every tume that a button of the keyboard is pressed,
     *                          while the GUI has its focus on the list of documents of the training set
     *                          del training set ranked to the current query document.
     */
private void keyPressedDocuments(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_keyPressedDocuments
    try {
        /**
         * If the button that has been pressed is 'Enter', all selected documents are opened
         */
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            for (int index : listDocuments.getSelectedRows()) {
                String path = (String) infoDocuments.get(index).get(documentClassifierViewResources.getString("listDocuments.title3"));
                openDocument(path);
            }
        }
    } catch (Exception ex) {
        showErrorMessage(ex.toString());
    }
}//GEN-LAST:event_keyPressedDocuments
    /**
     * This method shows an error message inside a JOptionPane, in order to make visualization of
     * exceptions' messages more user-friendly.
     * 
     * @param  msg              Message that must be showed.
     */
    public static void showErrorMessage(String msg) {
        JOptionPane.showMessageDialog(null, msg, ResourceBundle.getBundle("documentclassifier/resources/DocumentClassifierView").getString("messages.Error"), JOptionPane.ERROR_MESSAGE);
    }
    
    /**
     * This method implements the interface {@link PreferenceChangeListener}, and is called every
     * time that a value of a preferences node is removed or modified.
     * 
     * @param evt               The {@link PreferenceChangeEvent} generated every time that a value of a
     *                          preferences node is removed or modified.
     */
    public void preferenceChange(PreferenceChangeEvent evt) {
     /**
      * The method first controls if the modified value is the one containing the name
      * of the current metric or of the current scraper.
      */
        if(evt.getKey().equals(MapDefaultPreferences.METRIC)) {
            /**
             * In the 1st case, the method changes the title of the 1st column in the JTable containing
             * information about each document of the training set, ranked to the current query:
             */
            Vector<String> columnIdentifiers=new Vector<String>();
            if(application.getMetric(false).equals("TF-IDF")) {
                /**
                 * - If the current metric is now TF-IDF, the name of the 1st column of the JTable is set to "Cosine".
                 */
                columnIdentifiers.add(documentClassifierViewResources.getString("listDocuments.title0.Cosine"));
            }
            else {
                /**
                 * - Otherwise, if the current metric becomes the Bhattacharrya distance, that is set to "Distance".
                 */
                columnIdentifiers.add(documentClassifierViewResources.getString("listDocuments.title0.Distance"));
            }
            /**
             * The identifiers of the other two columns of the JTable (the 2nd and the 3rd) remain constant.
             */
            columnIdentifiers.add(tableModel.getColumnName(tableModel.getColumnCount()-2));
            columnIdentifiers.add(tableModel.getColumnName(tableModel.getColumnCount()-1));
            tableModel.setColumnIdentifiers(columnIdentifiers);
        } else if (evt.getKey().equals(MapDefaultPreferences.SCRAPER)) {
            /**
             * In the 2nd case, instead, the current scraper instance has been changed, therefore the property
             * 'documentRead' (which controls the activation of {@link OrdinaDocumentiTask}, the classification task
             * for the current document) is set to "false".
             * This is done to force the user to read, with the new chosen scraper, the current query document, before
             * trying to classify it.
             */
            setDocumentRead(false);
        }
    }
    
    /**
     * Action that shows a JOptionPane containing information about the program, its author, etc...
     */
    @Action
    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = DocumentClassifierApp.getApplication().getMainFrame();
            aboutBox = new DocumentClassifierAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        DocumentClassifierApp.getApplication().show(aboutBox);
    }
    
    /**
     * Action that shows the online help.
     */
    @Action
    public void showHelp() {
        JOptionPane.showMessageDialog(this.getFrame(), (Object) new String("Help"), "DocumentClassifier - Help", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Action that stops the current foreground task, if there is one running (that is, if the property 'taskRunning'
     * is set to "true").
     */
    @Action(enabledProperty = "taskRunning")
    public void stopCurrentTask() {
        DocumentClassifierApp.getInstance().getContext().getTaskMonitor().getForegroundTask().cancel(true);
        //currentTask.cancel(true);
    }
    
    /**
     * Property 'documentRead', which indicates if the document referenced by the URL
     * in the JTextField has already been read or not by the current scraper, and therefore
     * its title and text are available.
     */
    private boolean documentRead = false;
    public boolean isDocumentRead() {
        return documentRead;
    }
    
    public void setDocumentRead(boolean b) {
        boolean old = isDocumentRead();
        documentRead = b;
        firePropertyChange("documentRead", old, isDocumentRead());
    }
    
    /**
     * Property 'URLValid', which indicates if the address specified in the JTextField is valid or not.
     */
    private boolean URLValid = false;
    public boolean isURLValid() {
        return URLValid;
    }

    public void setURLValid(boolean b) {
        boolean old = isURLValid();
        this.URLValid = b;
        firePropertyChange("URLValid", old, isURLValid());
    }
    
    /**
     * Property 'taskRunning', which indicates in every moment if there is a running background task.
     */
    private boolean taskRunning = false;
    public boolean isTaskRunning() {
        return taskRunning;
    }
    
    public void setTaskRunning(boolean b) {
        boolean old = isTaskRunning();
        this.taskRunning = b;
        firePropertyChange("taskRunning", old, isTaskRunning());
    }
    
    /**
     * Action, and associated background task, to extract the title and text from the document whose
     * URL has been specified in the JTextField, and which represents the current query.
     * The action is enabled only if the property 'indirizzoValido' is set "true", something that
     * happens only if the JTextField of the URL is not empty.
     * 
     * @return              A reference to the background task associated with the action.
     */
    @Action(block = Task.BlockingScope.COMPONENT, enabledProperty = "URLValid")
    public Task readInputDocument() {
        currentTask=new ReadInputDocumentTask(application, fieldURL.getText());
        return currentTask;
    }
    
    private class ReadInputDocumentTask extends org.jdesktop.application.Task<Document, Void> {
        
        /**
         * The address of the current query document.
         */
        private String address;
        
        ReadInputDocumentTask(org.jdesktop.application.Application app, String URL) {
            // Runs on the EDT.  Copy GUI state that
            // doInBackground() depends on from parameters
            // to LeggiPaginaInputTask fields, here.
            super(app);
            this.address = URL.trim();
        }
        
        @Override
        protected Document doInBackground() {
            // Your Task's code here.  This method runs
            // on a background thread, so don't reference
            // the Swing GUI from here.
            try {
                /**
                 * The property 'taskRunning' is set to "true", such that it is possible to stop this task
                 * while it is running.
                 */
                setTaskRunning(true);
                /**
                 * The property 'documentRead' is set to "false", at least until the task doesn't finish without errors:
                 * In this case it means that it has been able to read and extract the title and text from the current document,
                 * and that property can therefore be set to "true".
                 */
                setDocumentRead(false);
                /**
                 * The text area with information on the current state of execution is cleaned.
                 */
                textAreaOutput.setText("");
                
                setMessage(documentClassifierViewResources.getString("messages.readingScrapingDocument"));
                /**
                 * The query document is scraped by the current scraper.
                 */
                URLConnection connection;
                String[] titleText;
                if (address.toLowerCase().startsWith("http://")) {
                    connection = (HttpURLConnection) new URL(address).openConnection();
                    titleText = application.readDocument(connection.getInputStream());
                } else if (address.toLowerCase().startsWith("https://")) {
                    connection=(HttpsURLConnection) new URL(address).openConnection();
                    titleText = application.readDocument(connection.getInputStream());
                } else if (address.toLowerCase().startsWith("file://")) {
                    titleText = application.readDocument(new FileInputStream(address.substring(7)));
                } else {
                    titleText = application.readDocument(new FileInputStream(address));
                }
                setMessage(documentClassifierViewResources.getString("messages.Document")+" "+documentClassifierViewResources.getString("messages.Read").toLowerCase());
                /**
                 * If exceptions have not been thrown during the scraping of the query document, a new instance
                 * of the class {@link Document} is created.
                 */
                currentQuery = new Document(titleText[0], titleText[1], address);
                return currentQuery;
            } catch (Exception ex) {
                currentQuery=new Document(address);
            } finally {
                return currentQuery;
            }
        }
        
        /**
         * If the task has reached its end without errors-->It is in the state 'Succeeded'-->
         * This method gets executed, and the result of task's computation (an instance of the class
         * {@link Document}) is passed in input to it.
         * 
         * @param result        The result of the computation of the task: An instance of the class
         *                      {@link Document}, representing the document just scraped.
         */
        @Override
        protected void succeeded(Document result) {
            // Runs on the EDT.  Update the GUI based on
            // the result computed by doInBackground().
            /**
             * The content of the scraped document is shown in the graphic interface,
             * and the property 'documentRead' is set to "true".
             */
            labelTitle.setText(result.getTitle());
            labelText.setText(result.getText());
            setDocumentRead(true);
        }
        
       /**
        * If instead the task has not completed its execution with success-->It is in the state 'Failed'-->
        * An error message is shown.
        * 
        * @param ex             An instance of the class {@link Throwable}, which represents the error
        *                       that has led to the state 'Failed'.
        */
        @Override
        protected void failed(Throwable ex) {
            showErrorMessage(ex.getMessage());
        }
        
        /**
         * In any case, whatever is the final state of the task at the end of its life cycle,
         * this method gets executed.
         */
        @Override
        protected void finished() {
            /**
             * In this implementation, it sets the string shown on the status bar, which represents
             * the current status of the task, to the empty one, and sets the value of the property
             * 'taskRunning' to "false", since the task has finished (successfully or not).
             */
            setMessage("");
            setTaskRunning(false);
        }
        
    }
    
    /**
     * Action, and associated background task, to rank the documents of the training set to the current query document.
     * The action is enabled only if the property 'documentRead' is set to "true", that is if the query document
     * has been correctly read from the URL provided in the JTextField.
     * 
     * @return                  A reference to the background task associated with the action.
     */
    @Action(block = Task.BlockingScope.COMPONENT, enabledProperty="documentRead")
    public Task rankDocuments() {
        currentTask = new RankDocumentsTask(getApplication(), currentQuery, application.getTrainingSet(), application.getKNN(false), true);
        return currentTask;
    }
    
    private class RankDocumentsTask extends org.jdesktop.application.Task<LinkedList<Map<String,Object>>, Void> {
        
        /**
         * The instance of the class {@link Document} that represents the current query.
         */
        private Document query;
        /**
         * The current training set.
         */
        private Set<Set<Document>> trainingSet;
        /**
         * The value of K for the K-NN to use during classification.
         */
        private int KNN;
        /**
         * A boolean variable, that indicates if the list of ranked documents of the training set
         * must be visualized or not.
         */
        private boolean visualizeListDocuments;
        
        /**
         * Constructor of the task.
         * 
         * @param app                       The instance of the class {@link org.jdesktop.application.Application Application} where this task is executed.
         * @param query                     The instance of the class {@link Document}, representing the current query to classify.
         * @param trainingSet               The current training set.
         * @param KNN                       The value of K for K-NN, to use during classification.
         * @param visualizzaListaDocumenti  A boolean value that indicates if the list of documents of the training set, ranked to the query, must be visualized or not.
         */
        RankDocumentsTask(org.jdesktop.application.Application app, Document query, Set<Set<Document>> trainingSet, int KNN, boolean visualizzaListaDocumenti) {
            // Runs on the EDT.  Copy GUI state that
            // doInBackground() depends on from parameters
            // to DeterminaCategoriaTask fields, here.
            super(app);
            this.query = query;
            this.trainingSet = trainingSet;
            this.KNN=KNN;
            this.visualizeListDocuments=visualizzaListaDocumenti;
        }

        @Override
        protected LinkedList<Map<String,Object>> doInBackground() {
            // Your Task's code here.  This method runs
            // on a background thread, so don't reference
            // the Swing GUI from here.
            try {
                /**
                 * The first thing done by the task, is to set the value of the property 'taskRunning' to "true",
                 * in order to be able to stop it while running.
                 */
                setTaskRunning(true);
                /**
                 * A list of elements of type 'Entry<Map<String,Object>, Map<String, Double>>' is created, where each one has
                 * as its key a map {@link String}->{@link Object}, containing information on a document of the training set,
                 * and as its value a map {@link String}->{@link Double}, containing the document's histogram.
                 * It is necessary to use a list in order to be able to order it, basing on the distance of each document
                 * from the current query (the distance is stored in the map that represents the key of each entry, at the voice
                 * "Distance").
                 */
                LinkedList<Entry<Map<String,Object>, Map<String, Double>>> listDocuments=new LinkedList<Entry<Map<String, Object>, Map<String, Double>>>();
                infoDocuments=new LinkedList<Map<String, Object>>();
                /**
                 * La list of ranked training set's document is cleared.
                 */
                tableModel.setRowCount(0);
                /**
                 * The name of the current metric is read.
                 */
                String metric=application.getMetric(false);
                
                setMessage(documentClassifierViewResources.getString("messages.readingTrainingSet")+"...");
                
                /**
                 * This map represents a single entry, in the list of information about all documents of the
                 * training set, and it's the key of each entry in the list 'listaDocuments'.
                 * The 1st instance created contains information on the query document.
                 */
                Map<String, Object> infoDocument=new LinkedHashMap<String, Object>();
                infoDocument.put("Distance", new Double(1.0));
                infoDocument.put("Categories", "Query");
                infoDocument.put("Title", labelTitle.getText());
                infoDocument.put("Path", fieldURL.getText());

                /**
                 * This map represents the histogram of each document of the training set, and it's the value
                 * of each entry in the list 'listDocuments'.
                 */
                Map<String, Double> histogram = new HashMap<String, Double>();
                /**
                 * This variable represents a single entry in the list 'listDocuments'.
                 */
                Entry<Map<String,Object>, Map<String, Double>> entryDocument;
                /**
                 * For each document of the training set, a new entry is added to the list, which contains:
                 * - Key: Information on the document.
                 * - Value: The histogram of the document.
                 */
                for(Set<Document> subSetTrainingSet : trainingSet ) {
                    for (Document currentDocument : subSetTrainingSet) {
                        infoDocument=new LinkedHashMap<String, Object>();
                        setMessage(documentClassifierViewResources.getString("messages.readingDocument")+" '" + currentDocument.getTitle() + "'...");
                        histogram=new HashMap<String, Double>(currentDocument.getHistogram());
                        infoDocument.put("Categories", currentDocument.getCategories());
                        infoDocument.put("Title", currentDocument.getTitle());
                        infoDocument.put("Path", currentDocument.getPath());
                        entryDocument=new AbstractMap.SimpleEntry<Map<String, Object>,Map<String,Double>>(infoDocument, histogram);
                        if(!listDocuments.contains(entryDocument)) {
                            listDocuments.add(entryDocument);
                        }
                    }
                }

                if (metric.equals("Bhattacharrya")) {
                    /**
                     * If the current metric is the Bhattacharrya distance-->It is necessary to calculate its value, between the 1st histogram
                     * (the one of the query document) and all others, and order this ones according to the values calculated.
                     */
                    setMessage(documentClassifierViewResources.getString("messages.bhattacharryaCalculation")+"...");
                    BhattacharryaDistanceComparator.calculateDistance(listDocuments, query.getHistogram());
                    setMessage("Ranking...");
                    Collections.sort((List<Entry<Map<String,Object>, Map<String, Double>>>) listDocuments, new BhattacharryaDistanceComparator());
                } else {
                    /** 
                     * Otherwise, if the current metric is the TF-IDF-->It is necessary to calculate the cosine of the angle between
                     * the TF-IDF vector of each training set's document, and the vector of the query document.
                     */
                    setMessage(documentClassifierViewResources.getString("messages.cosineCalculation")+"...");
                    TFIDFComparator.calculateCosine(listDocuments, query.getHistogram());
                    setMessage("Ranking...");
                    Collections.sort((List<Entry<Map<String,Object>, Map<String, Double>>>) listDocuments, new TFIDFComparator());
                }
                
                for(Entry<Map<String, Object>,Map<String,Double>> currentDocument : listDocuments) {
                    infoDocuments.add(currentDocument.getKey());
                }
                listDocuments=null;
                
                if(visualizeListDocuments) {
                    
                    for (int i = 0; i < infoDocuments.size(); i++) {
                        //i: Row number
                        Vector<Object> infoRow=new Vector<Object>();
                        infoDocument=infoDocuments.get(i);
                        /**
                         * For each document of the training set, its distance (by the current metric) is visualized, and, in the case of the TF-IDF,
                         * also the value of the angle between the corresponding document vector and the query vector.
                         */
                        infoRow.add(String.format("%6.5f",infoDocument.get("Distance")) + (metric.equals("TF-IDF") ? " ("+String.format("%4.2f",infoDocument.get("Angle"))+"°)" : ""));
                        infoRow.add(infoDocument.get("Categories"));
                        infoRow.add(infoDocument.get("Title"));
                        infoRow.add(infoDocument.get("Path"));
                        tableModel.addRow(infoRow);
                    }
                }
                /**
                 * The final result of the task, that must be returned to callers by the method {@link #get get}, is the list
                 * of information on training set's documents, ordered according to the distance (by the current metric)
                 * of each training set's document from the query.
                 */
                return infoDocuments;
            } catch (Exception ex) {
                failed(ex);
                return null;
            }
        }
        
        /**
         * If the task has reached the end of its execution flow without errors-->It is in the state 'Succeeded'-->
         * The following method is executed, getting in input the result of this task's computation.
         * The K-NN algorithm is then applied to the ordered list of training set's documents, to determine and
         * visualize the category of the current query document.
         * 
         * @param result        The list with information on training set's documents, ordered according
         *                      to the distance of each one to the query.
         */
        @Override
        protected void succeeded(LinkedList<Map<String,Object>> result) {
            // Runs on the EDT.  Update the GUI based on
            // the result computed by doInBackground().
            String category=determineCategory(result,KNN);
            setMessage(documentClassifierViewResources.getString("messages.estimatedCategory")+": '"+category + "'");
        }
        
       /**
        * If instead the task has not completed its execution successfully-->
        * It is in the state 'Failed'-->An error message is visualized.
        * 
        * @param ex             An instance of the class {@link Throwable}, which indicates
        *                       which error has brought the task to the state 'Failed'.
        */
        @Override
        protected void failed(Throwable ex) {
            showErrorMessage(ex.toString());
        }
        
        /**
         * In any case, whatever is the final state of the task, this method gets executed
         * at the end of its life cycle.
         * In this implementation, it sets the value of the property 'taskRunning' to "false",
         * since that task has finished (successfully or not).
         */
        @Override
        protected void finished() {
            setTaskRunning(false);
        }
        
    }
    
    /**
     * It determines the category of the query document, using the K-NN algorithm.
     * 
     * @param   infoDocuments   A list with information about the training set's documents, ordered according to the distance
     *                          of each one to the query document.
     * @param   K               The value of K for the K-NN-->The first K documents in the list will be taken into exam, to determine
     *                          the most frequent category, that will be assigned to the query document.
     * @return                  The name of the category determined with the K-NN algorithm.
     */
    private String determineCategory(LinkedList<Map<String, Object>> infoDocuments, int K) {

        /**
         * The following data structure is used by the method: An hash table, indexed on the names of the training set's categories,
         * and having as values pair of integers that represent, in order:
         * 1 - The minimum distance, from the query document, of one of the documents belonging to the category stored on the key,
         *     that appear among the first k of the list.
         * 2 - The frequency of the category stored on the key.
         * This data structure is used to determine the most frequent category among the ones of the first K documents, and to solve
         * this particular case: If two or more categories have the same frequency, the algorithm chooses the one that appears before
         * in the list, that is the one that has the 1st document less distant from the query.
         */
        Map<String, Entry<Integer, Integer>> categoriesFrequencies = new HashMap<String, Entry<Integer, Integer>>();
        Set<String> categoriesCurrentDocument;
        int maxFrequency, currentFrequency;
        Entry<Integer, Integer> currentEntry;
        Entry<String, Entry<Integer, Integer>> maxEntry = null;
        int numberExaminedCategories=0;
        boolean exit=false;
        for (int i = 0; i < K; i++) {
            categoriesCurrentDocument=(Set<String>) infoDocuments.get(i).get("Categories");
            for(String currentCategory : categoriesCurrentDocument) {
                numberExaminedCategories++;
                currentEntry = (SimpleEntry<Integer, Integer>) categoriesFrequencies.get(currentCategory);
                if (currentEntry == null) {
                    currentEntry = new AbstractMap.SimpleEntry<Integer,Integer>(i, 0);
                }
                currentEntry.setValue(currentEntry.getValue() + 1);
                //The frequency of the current category is incremented by 1
                categoriesFrequencies.put(currentCategory, currentEntry);
                
                if (i == 0) {
                    /**
                     * On the 1st iteration, the variable 'maxEntry' is initialized with:
                     *  - Key: The name of the category present in the 1st row of the input list.
                     *  - Value: Pair <0,1>.
                     */
                    maxEntry = new AbstractMap.SimpleEntry<String,Entry<Integer,Integer>>(currentCategory, currentEntry);
                }
                maxFrequency = maxEntry.getValue().getValue();
                currentFrequency = currentEntry.getValue();
                if (currentFrequency > maxFrequency ||
                        (currentFrequency == maxFrequency && currentEntry.getKey() < maxEntry.getValue().getKey())) {
                    maxEntry = new AbstractMap.SimpleEntry<String,Entry<Integer,Integer>>(currentCategory, currentEntry);
                }
            }
            if(exit) {
                break;
            }
            /**
             * If the next document has so much categories (more than 1) to reach or surpass the limit of K examined
             * categories-->The next iteration of the cycle will be the last.
             */
            if(i<=infoDocuments.size()-2 && numberExaminedCategories+((Set<String>)infoDocuments.get(i+1).get("Categories")).size()>=K) {
                exit=true;
            }
        }
        return maxEntry.getKey();
    }
    
    /**
     * Action, and associated background task, to perform K-Fold cross validation on all pages of the training set,
     * in order to determine the optimal value of K for the K-NN.
     * The task blocks the current window, and automatically shows a JOptionPane which, together with the appropriate
     * text area at the bottom of the main window, informs the user on the state of the computation.
     *  
     * @return                  Reference to the background task associated with this action.
     */
    @Action(block = Task.BlockingScope.WINDOW)
    public Task Validation() {
        return new ValidationTask(getApplication(),application.getMaximumKNNValidation(false));
    }
    
    private class ValidationTask extends org.jdesktop.application.Task<Entry<Integer,Double>, Void> {
        
        int KNNMinimum,KNNMaximum;
        boolean isStratified,isLogging;
        long startTime,stopTime;
        FileHandler FH;
        Logger infoLogger;
        
        ValidationTask(org.jdesktop.application.Application app,int KNNMaximum) {
            // Runs on the EDT.  Copy GUI state that
            // doInBackground() depends on from parameters
            // to trainTask fields, here.
            super(app);
            try {
                this.KNNMinimum = application.getKNNMinimum();
                this.KNNMaximum = KNNMaximum;
                this.isStratified = application.isStratified(false);
                this.isLogging=application.isLogging(false);
                if(isLogging) {
                    /**
                     * If logging is enabled, it is necessary to open the log file (with the name
                     * and mode specified through the preferences panel), and create and object
                     * of type {@link Logger}, to register 'INFO' messages.
                     */
                    FH = new FileHandler(application.getLogFile(false), !application.isOverwriteLogFile(false));
                    FH.setLevel(Level.INFO);
                    FH.setFormatter(new SimpleFormatter());
                    infoLogger = Logger.getLogger(DocumentClassifierView.class.getName());
                    for (Handler currentHandler : infoLogger.getHandlers()) {
                        infoLogger.removeHandler(currentHandler);
                    }
                    infoLogger.addHandler(FH);
                }
            } catch (Exception ex) {
                showErrorMessage(ex.getMessage());
                cancel(false);
            }
        }
        
        @Override
        protected Entry<Integer,Double> doInBackground() throws InterruptedException, ExecutionException {
            // Your Task's code here.  This method runs
            // on a background thread, so don't reference
            // the Swing GUI from here.
            
            /**
             * K-fold cross validation: Repeat inside a cycle these operations:
             * 1 - Partition (with random or stratified sampling) the training set in 'K-Fold' subsets
             * 2 - For the 1st value of K-NN (1):
             *  2.1 - For each subset of the partitioned training set (at point 1):
             *      2.1.1 - Temporarly delete it from the training set, and use it as the control set.
             *      2.1.2 - For each document of the control set (we already know its category), use it as the current query document.
             *          2.1.2.1 - Execute the task 'rankDocuments'.
             *                    At the end of it, save the list with information on documents of the training set (ordered according to
             *                    the distance with the current query), for future use, in a map, which associates to each query document
             *                    its own list of ranked training set's documents.
             *          2.1.2.2 - Compare the category returned by the task with the effective one of the document, and in case of mismatch
             *                    increment the counter of the classification error.
             *  2.2 - Calculate the average classification error for the current value of K-NN, and store it in a map.
             * 3 - For the next values of K-NN, from 2 to the maximum allowed one, it is not necessary to execute the point 2.1.2.1,
             *     since it's possible to use the list calculated and saved at point 2.1.2.1, and apply the K-NN to it.
             * 4 - Choose the value of K corresponding to the minimum average error.
             */
            startTime=Calendar.getInstance().getTimeInMillis();
            fieldURL.setText("");
            labelTitle.setText("");
            labelText.setText("");
            textAreaOutput.setText("");
            setDocumentRead(false);
            int KFold=application.getKFold(false);
            
            /**
             * The data structure 'minimumError' represents a single entry of a map, which at the end of the validation phase contains
             * the following:
             * - Key: The value of K (for the K-NN) corresponding to the minimum average classification error (average of the classification
             * error for each fold).
             * - Value: The value of such error.
             */
            Entry<Integer,Double> minimumError = new AbstractMap.SimpleEntry<Integer,Double>(0, Double.MAX_VALUE);
            /**
             * This map contains all associations 'Value of K'->'Corresponding classification error'.
             */
            Map<Integer, Double> averageErrors = new HashMap<Integer, Double>();
            /**
             * A copy of the training set is created. This is necessary, because if the sampling is stratified, each fold will be
             * created iterating on each category of the training set, and removing from it documents added to the current partition/fold.
             * Therefore, since the training set must not be modified, a new temporary copy of it is created, used in place of the original.
             */
            Set<Set<Document>> copyTrainingSet=new HashSet<Set<Document>>();
            for (Set<Document> currentSubSet : application.getTrainingSet() ) {
                Set<Document> copyCurrentSubSet=new HashSet<Document>();
                copyCurrentSubSet.addAll(currentSubSet);
                copyTrainingSet.add(copyCurrentSubSet);
            }
            
            Iterator randomIteratorTrainingSet=null;
            int numberCategories=copyTrainingSet.size();
            //Dimension of every fold
            int foldDimension=application.getTrainingSetSize()/KFold;
            int numberDocumentsForCategory=0;
            if(isStratified) {
                foldDimension = (foldDimension/numberCategories) * numberCategories;
                numberDocumentsForCategory=foldDimension/numberCategories;
            } else {
                Set<Document> normalizedTrainingSet=new HashSet<Document>();
                for(Set<Document> currentSubSet: copyTrainingSet) {
                    normalizedTrainingSet.addAll(currentSubSet);
                }
                randomIteratorTrainingSet=normalizedTrainingSet.iterator();
            }
            
            /**
             * A partitioning of the training set's copy is prepared (with random or stratified sampling),
             * in K-Fold subsets, all of the same dimension.
             */
            Set<Set<Document>> partitionedTrainingSet=new HashSet<Set<Document>>();
            Set<Document> trainingSetSubSet;
            Iterator iteratorTrainingSetCategory;                               //Iterator on the training set's categories
            for(int j=0;j<KFold;j++) {                                          //1
                trainingSetSubSet=new LinkedHashSet<Document>();
                if(isStratified) {
                    for(Set<Document> currentCategory: copyTrainingSet) {
                        iteratorTrainingSetCategory=currentCategory.iterator();
                        for(int z=0;z<numberDocumentsForCategory;z++) {
                            trainingSetSubSet.add((Document) iteratorTrainingSetCategory.next());
                            iteratorTrainingSetCategory.remove();
                        }
                    }
                } else {
                    for(int z=0;z<foldDimension;z++) {
                        trainingSetSubSet.add((Document) randomIteratorTrainingSet.next());
                    }
                }
                partitionedTrainingSet.add(trainingSetSubSet);
            }
            
            final int totalNumberIterations=(KNNMaximum-(KNNMinimum+1)+1)*foldDimension*KFold;
            int indexCurrentIteration=0;
            int indexCurrentDocument;
            int indexCurrentFold;
            int errorCounter;
            String calculatedCategory;
            Set<String> effectiveCategories;
            Set<Set<Document>> currentTrainingSet;
            String isStratificato=(application.isStratified(false)) ? "" : documentClassifierViewResources.getString("messages.Not")+" ";
            isStratificato+=documentClassifierViewResources.getString("messages.Stratified");
            isStratificato=isStratificato.toLowerCase();
            boolean orderDocuments;
            /**
             * The following map, named 'infoOrderedDocuments', is used to save the results of the ranking of training set's documents,
             * performed during the 1st iteration of the validation phase.
             * In this way the program will be able to reuse them in the next phase.
             */
            Map<Document,LinkedList<Map<String, Object>>> infoRankedDocuments=new HashMap<Document, LinkedList<Map<String, Object>>>();
            for (int i = KNNMinimum; i <= KNNMaximum; i++) {
                /**
                 * The boolean variable 'rankDocuments' indicates if the current iteration is the 1st or not, and therefore if it is
                 * necessary to perform ranking of training set's documents, or if it's possible to reuse the result saved during the
                 * 1st iteration, and avoid in this way unnecessary computations.
                 */
                orderDocuments=(i==KNNMinimum);
                String currentMessage="K-NN: "+i+"/"+KNNMaximum+" - K-Fold: "+KFold+" ["+documentClassifierViewResources.getString("messages.Partitioning")+" "+isStratificato+"] \n";
                if(orderDocuments) {
                    currentMessage += "("+documentClassifierViewResources.getString("messages.rankingOrderingDocuments")+"...)";
                }
                setMessage(currentMessage);
                textAreaOutput.append(currentMessage+"\n");
                if(isLogging) {         //If logging is enabled
                    infoLogger.info(currentMessage);
                }
                
                indexCurrentFold=0;
                errorCounter=0;
                for ( Set<Document> currentControlSet : partitionedTrainingSet ) {   //2.1
                    
                    indexCurrentFold++;
                    currentTrainingSet=new HashSet<Set<Document>>(partitionedTrainingSet);
                    currentTrainingSet.remove(currentControlSet);               //2.1.1
                    indexCurrentDocument=0;
                    for(Document currentQuery: currentControlSet) {             //2.1.2
                        indexCurrentDocument++;
                        if(!orderDocuments) {
                            indexCurrentIteration++;
                            setProgress(indexCurrentIteration,1,totalNumberIterations);
                        }
                        
                        if(application.isVisualizeCurrentDocument(false)) {
                            /**
                             * If visualization of current query document's information is enabled,
                             * its URL, title and text are shown in the GUI.
                             */
                            fieldURL.setText(currentQuery.getPath());
                            labelTitle.setText(currentQuery.getTitle());
                            labelText.setText(currentQuery.getText());
                        }
                        
                        if(orderDocuments) {                                    //2.1.2.1
                            currentTask = new RankDocumentsTask(getApplication(), currentQuery, currentTrainingSet, i, application.isVisualizeListRankedDocuments(false));
                            currentTask.execute();
                            infoRankedDocuments.put(currentQuery,((RankDocumentsTask)currentTask).get());
                            currentTask=null;
                        }
                        calculatedCategory = determineCategory(infoRankedDocuments.get(currentQuery), i);
                        effectiveCategories = currentQuery.getCategories();
                        if (!effectiveCategories.contains(calculatedCategory)) {//2.1.2.2
                            errorCounter++;
                        }
                        currentMessage=documentClassifierViewResources.getString("messages.Document")+" n° "+indexCurrentDocument+"/"+foldDimension+" (fold n° "+indexCurrentFold+"): "+documentClassifierViewResources.getString("messages.estimatedCategory")+": '"+calculatedCategory+"', "+documentClassifierViewResources.getString("messages.effectiveCategories")+": '"+effectiveCategories+"' - "+documentClassifierViewResources.getString("messages.currentNumberErrors")+": "+errorCounter;
                        textAreaOutput.append(currentMessage+"\n");
                        if(isLogging) {
                            infoLogger.info(currentMessage);
                        }
                        effectiveCategories=null;
                    }
                    currentTrainingSet=null;
                }
                double classificationError=((double)errorCounter/(KFold*foldDimension))*100;    //2.2
                currentMessage=documentClassifierViewResources.getString("messages.classificationError")+" "+documentClassifierViewResources.getString("messages.For").toLowerCase()+" K-NN="+i+" "+documentClassifierViewResources.getString("messages.And").toLowerCase()+" K-Fold="+KFold+": "+classificationError+"%";
                textAreaOutput.append(currentMessage+"\n");
                if(isLogging) {
                    infoLogger.info(currentMessage);
                }
                
                averageErrors.put(i, classificationError);
                
                if (classificationError < minimumError.getValue()) {            //4
                    minimumError = new AbstractMap.SimpleEntry<Integer,Double>(i, classificationError);
                }
            }
            return minimumError;
        }
        
        /**
         * If the task has reached the end of its execution flow without errors-->It is in the state 'Succeeded'-->
         * The following method is executed, getting in input the result of this task's computation (an instance
         * of the class 'Entry<Integer,Double>', containing the optimal value of K for K-NN, and the corresponding
         * classification error).
         * An appropriate status message is then visualized, and the value of K-NN is automatically set to the optimal
         * one.
         * 
         * @param result        An instance of the class 'Entry<Integer,Double>', containing in the key the optimal value
         *                      of K for K-NN, and in the value the correspondinig (minimum) classification error.
         */
        @Override
        protected void succeeded(Entry<Integer,Double> result) {
            try {
                // Runs on the EDT.  Update the GUI based on
                // the result computed by doInBackground().
                String currentMessage = documentClassifierViewResources.getString("messages.optimalValueKNN")+" " + result.getKey() + " ("+documentClassifierViewResources.getString("messages.classificationError")+": " + result.getValue() + "%)";
                setMessage(currentMessage);
                textAreaOutput.append(currentMessage+"\n");
                if(isLogging) {
                    infoLogger.info(currentMessage);
                }
                stopTime = Calendar.getInstance().getTimeInMillis();
                long elapsedTime = stopTime - startTime;
                textAreaOutput.append(documentClassifierViewResources.getString("messages.totalExecutionTime") + ": " + elapsedTime + " ms");
                application.setKNN(result.getKey());
            } catch (Exception ex) {
                showErrorMessage(ex.getMessage());
            }
        }
        
        /**
         * If the task is cancelled during execution-->The status message is set to the empty string.
         */
        @Override
        protected void cancelled() {
            setMessage("");
        }
        
       /**
        * If instead the task has not completed its execution without errors-->It is in the state 'Failed'-->
        * The status message is set to the empty string.
        * 
        * @param ex             An instance of the class {@link Throwable}, which indicates
        *                       which error has brought the task to the state 'Failed'.
        */
        @Override
        protected void failed(Throwable ex) {
            cancelled();
        }
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton buttonFind;
    private javax.swing.JButton buttonOpen;
    private javax.swing.JButton buttonRead;
    private javax.swing.JButton buttonStop;
    private javax.swing.JButton buttonValidation;
    private javax.swing.JTextField fieldURL;
    private javax.swing.JMenuItem helpMenuItem;
    private javax.swing.JTextArea labelText;
    private javax.swing.JLabel labelTitle;
    private javax.swing.JTable listDocuments;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem menuItemPreferenze;
    private javax.swing.ButtonGroup metricsGroup;
    private javax.swing.JMenu modifyMenu;
    private javax.swing.JMenuItem openMenuItem;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JScrollPane scrollPaneLabelText;
    private javax.swing.JScrollPane scrollPaneRankedDocuments;
    private javax.swing.JScrollPane scrollPaneTextAreaOutput;
    private javax.swing.JScrollPane scrollPanelLabelTitle;
    private javax.swing.JToolBar.Separator separatorInitial;
    private javax.swing.JToolBar.Separator separatorOpenTraining;
    private javax.swing.JSplitPane splitPaneHorizontal;
    private javax.swing.JSplitPane splitPaneVertical;
    private javax.swing.JLabel statusAnimationLabel;
    private javax.swing.JLabel statusMessageLabel;
    private javax.swing.JPanel statusPanel;
    private javax.swing.JTextArea textAreaOutput;
    private javax.swing.JToolBar toolbar;
    private javax.swing.JMenuItem trainingMenuItem;
    // End of variables declaration//GEN-END:variables
    private final Timer messageTimer;
    private final Timer busyIconTimer;
    private final Icon idleIcon;
    private final Icon[] busyIcons = new Icon[15];
    private int busyIconIndex = 0;
    private JDialog aboutBox;
    
}