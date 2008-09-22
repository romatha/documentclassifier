
package documentclassifier;

import documentclassifier.Metriche.TFIDFComparator;
import documentclassifier.Metriche.DistanzaBhattacharryaComparator;
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

/**
 * Rappresenta il frame principale dell'applicazione.
 * 
 * @author Salvo Danilo Giuffrida (giuffsalvo@hotmail.it, salvodanilogiuffrida@gmail.com)
 */
public class DocumentClassifierView extends FrameView implements PreferenceChangeListener {
    
    /**
     * L'istanza dell'applicazione a cui questo frame fa riferimento
     */
    private DocumentClassifierApp application;
    /**
     * Mantiene in ogni istante un riferimento al documento query corrente
     */
    private Documento queryCorrente;
    /**
     * Una lista di mappe 'String'->'Object' contenente informazioni su ogni documento del training set,
     * ed il cui contenuto è utilizzato per visualizzare tali informazioni nell'apposita JTable dopo la
     * fase di ranking rispetto alla query corrente
     */
    private LinkedList<Map<String,Object>> infoDocumenti = new LinkedList<Map<String, Object>>();
    /**
     * Ogni entry della lista di cui sopra è formata da un vettore di oggetti contenenti informazioni su ogni documento
     * (Distanza di Bhattacharrya/Coseno dell'angolo col documento query, Categoria della pagina a cui fà riferimento, Titolo della pagina),
     * e la mappa che rappresenta l'istogramma della pagina
     */
    private PreferenzeDialog preferenzeDialog;
    private Task taskCorrente;
    private DefaultTableModel tableModel;
    
    public DocumentClassifierView(SingleFrameApplication app) {

        super(app);
        initComponents();
        
        this.getFrame().setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        /**
         * La input map associata alla JTable che visualizza la lista dei documenti correlati al documento query viene
         * modificata corrente, in modo che quando l'utente preme invio su una voce, invece del comportamento di default
         * (scorrimento verso la voce successiva), possa essere implementato un comportamento personalizzato
         */
        listaDocumentiCorrelati.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
        /**
         * Setto la proprietà 'UpdatePolicy' del caret associato alla {@link JTextArea} 'textAreaOutput', contenente i messaggi
         * di stato durante l'esecuzione dei vari task del programma (lettura documento in input, classificazione, validazione
         * classificatore)
         */
        ((DefaultCaret) textAreaOutput.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        application = (DocumentClassifierApp) app;
        tableModel = (DefaultTableModel) listaDocumentiCorrelati.getModel();
        
        /**
         * Viene forzata la generazione di un evento di tipo 'PreferenceChangeEvent' (di cui questa classe è un listener),
         * per controllare qual'è la metrica scelta nelle preferenze, ed eventualmente cambiare il nome della 1a colonna
         * della tabella, da "Coseno" a "Distanza", nel caso in cui la metrica scelta sia quella di Bhattacharrya
         */
        preferenceChange(new PreferenceChangeEvent(application.getPreferenze(), "Metrica", application.getMetrica(false)));

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
        bottoneLeggi = new javax.swing.JButton();
        scrollPanelLabelTitolo = new javax.swing.JScrollPane();
        labelTitolo = new javax.swing.JLabel();
        bottoneStop = new javax.swing.JButton();
        bottoneCerca = new javax.swing.JButton();
        toolbar = new javax.swing.JToolBar();
        separatorIniziale = new javax.swing.JToolBar.Separator();
        bottoneApri = new javax.swing.JButton();
        separatorApriTraining = new javax.swing.JToolBar.Separator();
        bottoneValidation = new javax.swing.JButton();
        splitPaneVerticale = new javax.swing.JSplitPane();
        splitPaneOrizzontale = new javax.swing.JSplitPane();
        scrollPanePagineCorrelate = new javax.swing.JScrollPane();
        listaDocumentiCorrelati = new javax.swing.JTable();
        scrollPaneLabelTesto = new javax.swing.JScrollPane();
        labelTesto = new javax.swing.JTextArea();
        scrollPaneTextAreaOutput = new javax.swing.JScrollPane();
        textAreaOutput = new javax.swing.JTextArea();
        menuBar = new javax.swing.JMenuBar();
        javax.swing.JMenu fileMenu = new javax.swing.JMenu();
        apriMenuItem = new javax.swing.JMenuItem();
        trainingMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenuItem esciMenuItem = new javax.swing.JMenuItem();
        modificaMenu = new javax.swing.JMenu();
        menuItemPreferenze = new javax.swing.JMenuItem();
        javax.swing.JMenu aiutoMenu = new javax.swing.JMenu();
        aiutoMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();
        statusPanel = new javax.swing.JPanel();
        javax.swing.JSeparator statusPanelSeparator = new javax.swing.JSeparator();
        statusMessageLabel = new javax.swing.JLabel();
        statusAnimationLabel = new javax.swing.JLabel();
        progressBar = new javax.swing.JProgressBar();
        gruppoMetriche = new javax.swing.ButtonGroup();

        mainPanel.setName("mainPanel"); // NOI18N

        fieldURL.setName("fieldURL"); // NOI18N
        fieldURL.addCaretListener(new javax.swing.event.CaretListener() {
            public void caretUpdate(javax.swing.event.CaretEvent evt) {
                fieldURLCaretUpdate(evt);
            }
        });

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(documentclassifier.DocumentClassifierApp.class).getContext().getActionMap(DocumentClassifierView.class, this);
        bottoneLeggi.setAction(actionMap.get("LeggiDocumentoInput")); // NOI18N
        bottoneLeggi.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        bottoneLeggi.setName("bottoneLeggi"); // NOI18N

        scrollPanelLabelTitolo.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scrollPanelLabelTitolo.setFont(scrollPanelLabelTitolo.getFont());
        scrollPanelLabelTitolo.setName("scrollPanelLabelTitolo"); // NOI18N

        labelTitolo.setFont(new java.awt.Font("DejaVu Sans", 1, 12));
        labelTitolo.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        labelTitolo.setName("labelTitolo"); // NOI18N
        scrollPanelLabelTitolo.setViewportView(labelTitolo);

        bottoneStop.setAction(actionMap.get("stopTaskCorrente")); // NOI18N
        bottoneStop.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        bottoneStop.setName("bottoneStop"); // NOI18N

        bottoneCerca.setAction(actionMap.get("OrdinaDocumenti")); // NOI18N
        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(documentclassifier.DocumentClassifierApp.class).getContext().getResourceMap(DocumentClassifierView.class);
        bottoneCerca.setText(resourceMap.getString("bottoneCerca.text")); // NOI18N
        bottoneCerca.setName("bottoneCerca"); // NOI18N

        toolbar.setFloatable(false);
        toolbar.setRollover(true);
        toolbar.setName("toolbar"); // NOI18N

        separatorIniziale.setName("separatorIniziale"); // NOI18N
        toolbar.add(separatorIniziale);

        bottoneApri.setAction(actionMap.get("apriDocumento")); // NOI18N
        bottoneApri.setText(resourceMap.getString("bottoneApri.text")); // NOI18N
        bottoneApri.setFocusable(false);
        bottoneApri.setName("bottoneApri"); // NOI18N
        bottoneApri.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        toolbar.add(bottoneApri);

        separatorApriTraining.setName("separatorApriTraining"); // NOI18N
        toolbar.add(separatorApriTraining);

        bottoneValidation.setAction(actionMap.get("Validation")); // NOI18N
        bottoneValidation.setText(resourceMap.getString("bottoneValidation.text")); // NOI18N
        bottoneValidation.setFocusable(false);
        bottoneValidation.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        bottoneValidation.setName("bottoneValidation"); // NOI18N
        bottoneValidation.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        toolbar.add(bottoneValidation);

        splitPaneVerticale.setDividerLocation(420);
        splitPaneVerticale.setDividerSize(10);
        splitPaneVerticale.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        splitPaneVerticale.setContinuousLayout(true);
        splitPaneVerticale.setName("splitPaneVerticale"); // NOI18N
        splitPaneVerticale.setOneTouchExpandable(true);

        splitPaneOrizzontale.setDividerLocation(360);
        splitPaneOrizzontale.setDividerSize(10);
        splitPaneOrizzontale.setContinuousLayout(true);
        splitPaneOrizzontale.setName("splitPaneOrizzontale"); // NOI18N
        splitPaneOrizzontale.setOneTouchExpandable(true);

        scrollPanePagineCorrelate.setName("scrollPanePagineCorrelate"); // NOI18N

        listaDocumentiCorrelati.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Coseno", "Categorie", "Titolo"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class
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
        listaDocumentiCorrelati.setName("listaDocumentiCorrelati"); // NOI18N
        listaDocumentiCorrelati.getTableHeader().setReorderingAllowed(false);
        listaDocumentiCorrelati.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                clickPaginaCorrelata(evt);
            }
        });
        listaDocumentiCorrelati.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                keyPressedPaginaCorrelata(evt);
            }
        });
        scrollPanePagineCorrelate.setViewportView(listaDocumentiCorrelati);
        listaDocumentiCorrelati.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        listaDocumentiCorrelati.getColumnModel().getColumn(0).setHeaderValue(resourceMap.getString("listaDocumentiCorrelati.columnModel.title0")); // NOI18N
        listaDocumentiCorrelati.getColumnModel().getColumn(2).setHeaderValue(resourceMap.getString("listaDocumentiCorrelati.columnModel.title2")); // NOI18N

        splitPaneOrizzontale.setRightComponent(scrollPanePagineCorrelate);

        scrollPaneLabelTesto.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPaneLabelTesto.setName("scrollPaneLabelTesto"); // NOI18N

        labelTesto.setColumns(20);
        labelTesto.setEditable(false);
        labelTesto.setLineWrap(true);
        labelTesto.setRows(1);
        labelTesto.setWrapStyleWord(true);
        labelTesto.setName("labelTesto"); // NOI18N
        scrollPaneLabelTesto.setViewportView(labelTesto);

        splitPaneOrizzontale.setLeftComponent(scrollPaneLabelTesto);

        splitPaneVerticale.setTopComponent(splitPaneOrizzontale);

        scrollPaneTextAreaOutput.setName("scrollPaneTextAreaOutput"); // NOI18N

        textAreaOutput.setEditable(false);
        textAreaOutput.setLineWrap(true);
        textAreaOutput.setWrapStyleWord(true);
        textAreaOutput.setName("textAreaOutput"); // NOI18N
        scrollPaneTextAreaOutput.setViewportView(textAreaOutput);

        splitPaneVerticale.setRightComponent(scrollPaneTextAreaOutput);

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(toolbar, javax.swing.GroupLayout.DEFAULT_SIZE, 819, Short.MAX_VALUE)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(fieldURL, javax.swing.GroupLayout.DEFAULT_SIZE, 705, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bottoneLeggi, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bottoneStop, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scrollPanelLabelTitolo, javax.swing.GroupLayout.DEFAULT_SIZE, 719, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bottoneCerca)
                .addContainerGap())
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(splitPaneVerticale)
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
                        .addComponent(bottoneStop, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(bottoneLeggi))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(bottoneCerca)
                    .addComponent(scrollPanelLabelTitolo, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(splitPaneVerticale, javax.swing.GroupLayout.DEFAULT_SIZE, 491, Short.MAX_VALUE)
                .addContainerGap())
        );

        mainPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {bottoneLeggi, bottoneStop, fieldURL});

        menuBar.setName("menuBar"); // NOI18N

        fileMenu.setMnemonic('F');
        fileMenu.setText(resourceMap.getString("fileMenu.text")); // NOI18N
        fileMenu.setName("fileMenu"); // NOI18N

        apriMenuItem.setAction(actionMap.get("apriDocumento")); // NOI18N
        apriMenuItem.setText(resourceMap.getString("apriMenuItem.text")); // NOI18N
        apriMenuItem.setName("apriMenuItem"); // NOI18N
        fileMenu.add(apriMenuItem);

        trainingMenuItem.setAction(actionMap.get("Validation")); // NOI18N
        trainingMenuItem.setText(resourceMap.getString("trainingMenuItem.text")); // NOI18N
        trainingMenuItem.setName("trainingMenuItem"); // NOI18N
        fileMenu.add(trainingMenuItem);

        esciMenuItem.setAction(actionMap.get("quit")); // NOI18N
        esciMenuItem.setName("esciMenuItem"); // NOI18N
        fileMenu.add(esciMenuItem);

        menuBar.add(fileMenu);

        modificaMenu.setMnemonic('M');
        modificaMenu.setText(resourceMap.getString("modificaMenu.text")); // NOI18N
        modificaMenu.setName("modificaMenu"); // NOI18N

        menuItemPreferenze.setText(resourceMap.getString("menuItemPreferenze.text")); // NOI18N
        menuItemPreferenze.setName("menuItemPreferenze"); // NOI18N
        menuItemPreferenze.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                apriPreferenze(evt);
            }
        });
        modificaMenu.add(menuItemPreferenze);

        menuBar.add(modificaMenu);

        aiutoMenu.setMnemonic('A');
        aiutoMenu.setText(resourceMap.getString("aiutoMenu.text")); // NOI18N
        aiutoMenu.setName("aiutoMenu"); // NOI18N

        aiutoMenuItem.setAction(actionMap.get("showHelp")); // NOI18N
        aiutoMenuItem.setText(resourceMap.getString("aiutoMenuItem.text")); // NOI18N
        aiutoMenuItem.setName("aiutoMenuItem"); // NOI18N
        aiutoMenu.add(aiutoMenuItem);

        aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
        aboutMenuItem.setName("aboutMenuItem"); // NOI18N
        aiutoMenu.add(aboutMenuItem);

        menuBar.add(aiutoMenu);

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
    /**
     * Azione associata al menù File->Apri, che apre un JFileChooser con la quale
     * l'utente può aprire un documento da disco da classificare
     */
    private JFileChooser fileChooser;
    @Action
    public void apriDocumento() {
        try {
            if(fileChooser==null) {
                fileChooser = new JFileChooser();
                fileChooser.setCurrentDirectory(new File("."));
            }
            if (fileChooser.showOpenDialog(this.getComponent()) == JFileChooser.APPROVE_OPTION) {
                fieldURL.setText(fileChooser.getSelectedFile().getCanonicalPath());
            }
        } catch (IOException ex) {
            mostraMessaggioErrore(ex.toString());
            ex.printStackTrace();
        }
    }
    
    /**
     * Questo metodo viene chiamato ogni qualvolta l'utente fà doppio click, o preme Invio, su una o più voci
     * della JTable contentente la lista dei documenti del training set correlati al documento query corrente.
     * Esso viene quindi chiamato per ognuna delle voci della lista che sono state selezionate e su cui si è fatto
     * click o si è premuto invio, e si occupa di aprire in un browser esterno i documenti originali a cui esse
     * si riferiscono
     * 
     * @param indice            L'indice della voce all'interno della lista dei documenti del training set,
     *                          di cui bisogna aprire il documento originale a cui si riferisce
     * @throws java.lang.Exception
     */
    private void apriDocumentoSelezionato(int indice) throws Exception {
        
        String path=(String) infoDocumenti.get(indice).get("Percorso");
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
     * Quando l'utente fà doppio click su una delle voci nella lista delle pagine correlate,
     * questo metodo gestisce l'apertura della corrispondente pagina web in un browser
     * 
     * @param  evt  Il MouseEvent da gestire
     */
    private void clickPaginaCorrelata(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_clickPaginaCorrelata
        try {
            int NumeroDoppiClick = evt.getClickCount() / 2;
            if (NumeroDoppiClick >= 1) {
                for(int indice : listaDocumentiCorrelati.getSelectedRows()) {
                    apriDocumentoSelezionato(indice);
                }
            }
        } catch (Exception ex) {
            mostraMessaggioErrore(ex.toString());
            ex.printStackTrace();
        }
}//GEN-LAST:event_clickPaginaCorrelata

    /**
     * Gestisce l'ActionEvent generato facendo click sulla voce di menù Modifica->Preferenze,
     * rendendo visibile il pannello per la configurazione delle preferenze
     * 
     * @param evt   L'ActionEvent da gestire
     */
private void apriPreferenze(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_apriPreferenze
    preferenzeDialog = new PreferenzeDialog(this, true);
    preferenzeDialog.setVisible(true);
}//GEN-LAST:event_apriPreferenze

    /**
     * Viene chiamato ogni volta che il JTextField che contiene la URL del documento
     * da classificare (locale o di rete) cambia posizione del caret.
     * 
     * @param evt               Il {@link javax.swing.event.CaretEvent CaretEvent} generato
     *                          ogni volta che il caret del JTextField della URL del documento
     *                          corrente cambia posizione (per esempio a causa dell'inserimento
     *                          o cancellazione di caratteri)
     */
private void fieldURLCaretUpdate(javax.swing.event.CaretEvent evt) {//GEN-FIRST:event_fieldURLCaretUpdate
    /**
     * Ogni volta che la posizione del caret cambia, viene controllato se ciò è dovuto
     * alla cancellazione o all'inserimento di caratteri nel JTextField della URL.
     * Nel caso vengano cancellati tanti caratteri da rendere vuoto tale JTextField,
     * la proprietà 'indirizzoValido', che controlla l'abilitazione del task di lettura
     * del documento corrente da classificare ({@link LeggiDocumentoInputTask}), viene
     * settata a "false", altrimenti a "true"
     */
    String URLText = fieldURL.getText();
    if (!URLText.isEmpty()) {
        setIndirizzoValido(true);
    } else {
        setIndirizzoValido(false);
    }
}//GEN-LAST:event_fieldURLCaretUpdate

    /**
     * Viene chiamato ogni volta che un pulsante viene premuto mentre l'intefaccia grafica ha il focus
     * sulla lista dei documenti del training set correlati al documento query corrente
     * 
     * @param evt               Il KeyEvent generato ogni volta che un pulsante viene premuto
     *                          mentre l'intefaccia grafica ha il focus sulla lista dei documenti
     *                          del training set correlati al documento query corrente
     */
private void keyPressedPaginaCorrelata(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_keyPressedPaginaCorrelata
    try {
        /**
         * Se il pulsante che è stato premuto è l'Invio, vengono aperti (in un browser esterno)
         * tutti i documenti relativi alle voci della lista correntemente selezionate
         */
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            for(int indice : listaDocumentiCorrelati.getSelectedRows()) {
                    apriDocumentoSelezionato(indice);
                }
        }
    } catch (Exception ex) {
        mostraMessaggioErrore(ex.toString());
        ex.printStackTrace();
    }
}//GEN-LAST:event_keyPressedPaginaCorrelata
    /**
     * Mostra un messaggio di errore dentro un JOptionPane, per rendere più user-friendly
     * la visualizzazione delle eccezzioni
     * 
     * @param  msg              Testo del messaggio di errore da visualizzare 
     */
    public static void mostraMessaggioErrore(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Errore", JOptionPane.ERROR_MESSAGE);
    }
    
    /**
     * Implementa l'interfaccia {@link PreferenceChangeListener}, e viene chiamato ogni
     * volta che vengono rimossi o modificati i valori di un nodo delle preferenze.
     * <p>
     * Ciò che fà questa implementazione del metodo è di controllare se la preferenza
     * modificata è quella relativa alla metrica corrente o allo scraper corrente.
     * <p>
     * Nel 1° caso, infatti, viene cambiato il titolo della 1a colonna della JTable
     * contenente le informazioni su tutti i documenti del training set, ordinati in base
     * alla distanza dal documento query:
     * <p>
     * - Se la metrica corrente diventa la TF-IDF, il nome della 1a colonna della JTable
     * viene settato a "Coseno"
     * <p>
     * - Altrimenti, se la metrica corrente diventa la distanza di Bhattacharrya, esso
     * viene settato a "Distanza"
     * <p>
     * Nel 2° caso, invece, se cambia lo scraper corrente viene settata a "false" la proprietà
     * 'documentoLetto', che controlla l'attivazione del task di classificazione del documento
     * query corrente ({@link OrdinaDocumentiTask}). Questo per costringere l'utente a rileggere,
     * con il nuovo scraper scelto anzichè il precedente, il documento referenziato dalla URL corrente,
     * prima di tentare di classificarlo.
     * 
     * @param evt               Il {@link PreferenceChangeEvent} generato ogni volta che si modifica o
     *                          elimina un valore da un nodo delle preferenze
     */
    public void preferenceChange(PreferenceChangeEvent evt) {
        if(evt.getKey().equals("Metrica")) {
            Vector<String> columnIdentifiers=new Vector<String>();
            if(application.getMetrica(false).equals("TF-IDF")) {
                columnIdentifiers.add("Coseno");
            }
            else {
                columnIdentifiers.add("Distanza");
            }
            /**
             * Gli identificatori delle altre due colonne (la 2a e la 3a) rimangono costanti
             */
            columnIdentifiers.add(tableModel.getColumnName(tableModel.getColumnCount()-2));
            columnIdentifiers.add(tableModel.getColumnName(tableModel.getColumnCount()-1));
            tableModel.setColumnIdentifiers(columnIdentifiers);
        } else if (evt.getKey().equals("Scraper")) {
            setDocumentoLetto(false);
        }
    }
    
    /**
     * Azione che mostra un JOptionPane con informazioni sul programma, l'autore, ecc...
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
     * Azione che mostra l'help in linea
     */
    @Action
    public void showHelp() {
        JOptionPane.showMessageDialog(this.getFrame(), (Object) new String("Help"), "WebClassifier - Help", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Azione che ferma il task corrente, se c'è n'è uno in corso d'esecuzione,
     * cioè se la proprietà 'taskRunning' è vera 
     */
    @Action(enabledProperty = "taskRunning")
    public void stopTaskCorrente() {
        taskCorrente.cancel(true);
    }
    
    /**
     * Proprietà 'documentoLetto', che indica se il documento referenziato dalla URL
     * specificata nell'apposito JTextField è già stato letto o meno dallo scraper corrente,
     * e quindi il suo titolo ed il suo testo sono disponibili per essere usati per
     * il confronto e la classificazione nei confronti del training set
     */
    protected boolean documentoLetto = false;
    public boolean isDocumentoLetto() {
        return documentoLetto;
    }
    
    public void setDocumentoLetto(boolean b) {
        boolean old = isDocumentoLetto();
        documentoLetto = b;
        firePropertyChange("documentoLetto", old, isDocumentoLetto());
    }
    
    /**
     * Proprietà 'indirizzoValido', che indica se l'indirizzo specificato nel JTextField
     * della URL corrente è valido o meno
     */
    private boolean indirizzoValido = false;
    public boolean isIndirizzoValido() {
        return indirizzoValido;
    }

    public void setIndirizzoValido(boolean b) {
        boolean old = isIndirizzoValido();
        this.indirizzoValido = b;
        firePropertyChange("indirizzoValido", old, isIndirizzoValido());
    }
    
    /**
     * La proprietà 'taskRunning', che indica in ogni istante se c'è un task di background in esecuzione
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
     * Azione, e task di background associato, per estrarre il titolo ed il testo del documento
     * la cui URL è specificata nell'apposito JTextField, e che costituirà la query corrente.
     * L'azione è possibile solo se la proprietà 'indirizzoValido' è vera, cosa che accade solo
     * se il JTextField della URL non è vuoto
     * 
     * @return                  Un riferimento al task di background associato all'azione
     */
    @Action(block = Task.BlockingScope.COMPONENT, enabledProperty = "indirizzoValido")
    public Task LeggiDocumentoInput() {
        taskCorrente=new LeggiDocumentoInputTask(application, fieldURL.getText());
        return taskCorrente;
    }
    
    private class LeggiDocumentoInputTask extends org.jdesktop.application.Task<Documento, Void> {
        
        /**
         * L'indirizzo della pagina da cui estrarre il titolo ed il testo, utilizzando lo scraper corrente
         */
        private String indirizzo;
        
        LeggiDocumentoInputTask(org.jdesktop.application.Application app, String URL) {
            // Runs on the EDT.  Copy GUI state that
            // doInBackground() depends on from parameters
            // to LeggiPaginaInputTask fields, here.
            super(app);
            this.indirizzo = URL.trim();
        }
        
        @Override
        protected Documento doInBackground() {
            // Your Task's code here.  This method runs
            // on a background thread, so don't reference
            // the Swing GUI from here.
            try {
                /**
                 * La proprietà 'taskRunning' viene settata a "true", in modo che sia possibile fermare questo task
                 * mentre è in esecuzione
                 */
                setTaskRunning(true);
                /**
                 * La proprietà 'documentoLetto' viene resettata a "false", almeno finchè non si arriva alla conclusione
                 * del task senza errori, in tal caso significa che il task è riuscito a leggere ed estrarre il testo dal
                 * documento corrente, e la proprietà in oggetto può essere resettata a true
                 */
                setDocumentoLetto(false);
                /**
                 * L'area di testo con informazioni sullo stato corrente di esecuzione viene ripulita
                 */
                textAreaOutput.setText("");
                
                setMessage("Lettura e parsing documento in corso...");
                /**
                 * Il documento referenziato dalla URL corrente viene letto dallo scraper corrente
                 */
                URLConnection Connessione;
                String[] titoloTesto;
                if (indirizzo.toLowerCase().startsWith("http://")) {
                    Connessione = (HttpURLConnection) new URL(indirizzo).openConnection();
                    titoloTesto = application.leggiDocumento(Connessione.getInputStream());
                } else if (indirizzo.toLowerCase().startsWith("https://")) {
                    Connessione=(HttpsURLConnection) new URL(indirizzo).openConnection();
                    titoloTesto = application.leggiDocumento(Connessione.getInputStream());
                } else if (indirizzo.toLowerCase().startsWith("file://")) {
                    titoloTesto = application.leggiDocumento(new FileInputStream(indirizzo.substring(7)));
                } else {
                    titoloTesto = application.leggiDocumento(new FileInputStream(indirizzo));
                }
                setMessage("Documento letto...");
                /**
                 * Se non sono state lanciate eccezzioni durante la lettura e l'estrazione del testo, viene creata
                 * una nuova istanza della classe {@link Documento}
                 */
                queryCorrente = new Documento(titoloTesto[0], titoloTesto[1], indirizzo);
                return queryCorrente;
            } catch (Exception ex) {
                queryCorrente=new Documento(indirizzo);
                //failed(ex);
                //return null;
            } finally {
                return queryCorrente;
            }
        }
        
        /**
         * Se il task ha raggiungo la fine del suo flusso di esecuzione senza errori-->
         * Si trova nello stato 'Succeeded'-->Viene eseguito questo metodo, a cui viene passato
         * in input il risultato della computazione (un'istanza della classe {@link Documento}).
         * Il contenuto del documento appena letto viene quindi mostrato nell'interfaccia grafica,
         * e la proprietà 'documentoLetto' viene settata a "true"
         * 
         * @param result        Il risultato della computazione del task: Un'istanza della classe
         *                      {@link Documento} rappresentante il documento appena letto
         */
        @Override
        protected void succeeded(Documento result) {
            // Runs on the EDT.  Update the GUI based on
            // the result computed by doInBackground().
            labelTitolo.setText(result.getTitolo());
            labelTesto.setText(result.getTesto());
            setDocumentoLetto(true);
        }
        
       /**
        * Se invece il task non ha completato la sua esecuzione con successo-->
        * Si trova nello stato 'Failed''-->Viene mostrato un messaggio di errore
        * 
        * @param ex             Un'istanza della classe {@link Throwable} che indica
        *                       l'errore che ha portato allo stato 'Failed'
        */
        @Override
        protected void failed(Throwable ex) {
            mostraMessaggioErrore(ex.getMessage());
            ex.printStackTrace();
        }
        
        /**
         * In ogni caso, qualunque sia lo stato finale del task alla fine del suo
         * ciclo di vita, questo metodo viene eseguito per ultimo.
         * In questa implementazione, setta la stringa visualizzata sulla barra di
         * stato, rappresentante lo stato corrente del task, alla stringa vuota,
         * e setta il valore della proprietà 'taskRunning' a "false", dato che
         * il task si è concluso (con successo o meno)
         */
        @Override
        protected void finished() {
            setMessage("");
            setTaskRunning(false);
        }
        
    }
    
    /**
     * Azione, e task di background associato, per fare il ranking dei documenti
     * del training set rispetto al documento query corrente.
     * L'azione è abilitata solo se la proprietà 'documentoLetto' è settata a "true",
     * se cioè il documento query è stato letto correttamente dalla URL fornita
     * nell'apposito JTextField
     *  
     * @return                  Un riferimento al task di background associato all'azione
     */
    @Action(block = Task.BlockingScope.COMPONENT, enabledProperty="documentoLetto")
    public Task OrdinaDocumenti() {
        taskCorrente = new OrdinaDocumentiTask(getApplication(), queryCorrente, application.getTrainingSet(), application.getKNN(false), true);
        return taskCorrente;
    }
    
    private class OrdinaDocumentiTask extends org.jdesktop.application.Task<LinkedList<Map<String,Object>>, Void> {
        
        /**
         * L'istanza della classe documento che rappresenta la query corrente
         */
        private Documento query;
        /**
         * Il training set rispetto a cui classificare la query
         */
        private Set<Set<Documento>> trainingSet;
        /**
         * Il valore di K per il K-NN da usare in fase di classificazione, dopo aver fatto il ranking dei documenti del training set
         */
        private int KNN;
        /**
         * Un valore booleano che indica se visualizzare nell'apposita JTable la lista dei documenti del training set
         * ordinati in base alla similarità col document query corrente
         */
        private boolean visualizzaListaDocumenti;
        
        /**
         * Costruttore del task
         * 
         * @param app                       L'instanza della classe {@link org.jdesktop.application.Application Application} in cui questo task viene eseguito
         * @param query                     L'istanza della classe {@link Documento} rappresentante la query corrente da classificare
         * @param trainingSet               Il training set corrente rispetto a cui classificare la query
         * @param KNN                       Il valore di K per il KNN da usare in fase di classificazione
         * @param visualizzaListaDocumenti  Se visualizzare o meno la lista dei documenti ordinati in base alla similarità
         */
        OrdinaDocumentiTask(org.jdesktop.application.Application app, Documento query, Set<Set<Documento>> trainingSet, int KNN, boolean visualizzaListaDocumenti) {
            // Runs on the EDT.  Copy GUI state that
            // doInBackground() depends on from parameters
            // to DeterminaCategoriaTask fields, here.
            super(app);
            this.query = query;
            this.trainingSet = trainingSet;
            this.KNN=KNN;
            this.visualizzaListaDocumenti=visualizzaListaDocumenti;
        }

        @Override
        protected LinkedList<Map<String,Object>> doInBackground() {
            // Your Task's code here.  This method runs
            // on a background thread, so don't reference
            // the Swing GUI from here.
            try {
                /**
                 * Per prima cosa si setta il valore della proprietà 'taskRunning' a true, in modo che sia
                 * possibile se necessario cancellare questo task mentre è in esecuzione
                 */
                setTaskRunning(true);
                /**
                 * Si crea una lista di elementi di tipo 'Entry<Map<String,Object>, Map<String, Double>>', ciascuno dei
                 * quali rappresenta un documento del training set, e nella quale la chiave è una mappa 'String'->'Object',
                 * contenente informazioni sul documento, ed il valore è una mappa 'String'->'Double', contenente l'istogramma
                 * del documento.
                 * E' necessario usare una lista per poterla successivamente ordinare, in base alla distanza di ogni documento
                 * dalla query corrente (distanza memorizzata nella mappa della chiave, alla voce 'Distanza')
                 */
                LinkedList<Entry<Map<String,Object>, Map<String, Double>>> listaDocumenti=new LinkedList<Entry<Map<String, Object>, Map<String, Double>>>();
                /**
                 * La lista con le informazioni su ogni documento viene reinizializzata
                 */
                infoDocumenti=new LinkedList<Map<String, Object>>();
                /**
                 * La lista dei documenti viene ripulita
                 */
                tableModel.setRowCount(0);
                /**
                 * Il nome della metrica corrente viene letto
                 */
                String metrica=application.getMetrica(false);
                
                setMessage("Lettura training set...");
                
                /**
                 * Questa mappa rappresenta una singola entry della lista di informazioni su tutti i documenti
                 * del training set, ed è la chiave di ogni entry della lista 'listaDocumenti'.
                 * La 1a istanza creata contiene informazioni sul documento query
                 */
                Map<String, Object> informazioniDocumento=new LinkedHashMap<String, Object>();
                informazioniDocumento.put("Distanza", new Double(1.0));
                informazioniDocumento.put("Categorie", "Query");
                informazioniDocumento.put("Titolo", labelTitolo.getText());
                informazioniDocumento.put("Percorso", fieldURL.getText());

                /**
                 * Questa mappa rappresenta l'istogramma di ogni documento del training set, ed è il valore di
                 * ogni entry della lista 'listaDocumenti'
                 */
                Map<String, Double> istogramma = new HashMap<String, Double>();
                /**
                 * Questa variabile rappresenta una singola entry della lista 'listaDocumenti'
                 */
                Entry<Map<String,Object>, Map<String, Double>> entryDocumento;
                /**
                 * Per ogni documento del training set, viene aggiunta una nuova entry nell'apposita lista, che contiene:
                 * - Informazioni sul documento
                 * - L'istogramma del documento
                 */
                for(Set<Documento> sottoInsiemeTrainingSet : trainingSet ) {
                    for (Documento documentoCorrente : sottoInsiemeTrainingSet) {
                        informazioniDocumento=new LinkedHashMap<String, Object>();
                        setMessage("Lettura documento '" + documentoCorrente.getTitolo() + "'...");
                        istogramma=new HashMap<String, Double>(documentoCorrente.getIstogramma());
                        informazioniDocumento.put("Categorie", documentoCorrente.getCategorie());
                        informazioniDocumento.put("Titolo", documentoCorrente.getTitolo());
                        informazioniDocumento.put("Percorso", documentoCorrente.getPercorso());
                        entryDocumento=new AbstractMap.SimpleEntry<Map<String, Object>,Map<String,Double>>(informazioniDocumento, istogramma);
                        if(!listaDocumenti.contains(entryDocumento)) {
                            listaDocumenti.add(entryDocumento);
                        }
                    }
                }

                if (metrica.equals("Bhattacharrya")) {
                    /**
                     * Se la metrica corrente è la distanza di Bhattacharrya-->Bisogna calcolarne il valore tra il 1° istogramma
                     * (quello della query) e tutti gli altri, ed ordinare gli istogrammi dei documenti del training set di conseguenza
                     */
                    setMessage("Calcolo distanze di Bhattacharrya...");
                    DistanzaBhattacharryaComparator.calcolaDistanza(listaDocumenti, query.getIstogramma());
                    setMessage("Ranking...");
                    Collections.sort((List<Entry<Map<String,Object>, Map<String, Double>>>) listaDocumenti, new DistanzaBhattacharryaComparator());
                } else {
                    /* 
                     * Adesso bisogna calcolare il coseno dell'angolo tra i vettori TF-IDF di ogni
                     * documento ed il vettore query
                     */
                    setMessage("Calcolo coseni...");
                    TFIDFComparator.calcolaCoseno(listaDocumenti, query.getIstogramma());
                    setMessage("Ranking...");
                    Collections.sort((List<Entry<Map<String,Object>, Map<String, Double>>>) listaDocumenti, new TFIDFComparator());
                }
                /**
                 * Vengono aggiunti, alla lista con le informazioni di tutti i documenti, tutte le informazioni presenti nelle
                 * chiavi della lista dei documenti, già ordinata. La ragione per cui queste informazioni vengono inserite in
                 * tale lista in questo punto è proprio perchè da qui in poi la lista dei documenti del training set è ordinata
                 * in base alla distanza di ogni documento dalla query, quindi anche la lista delle informazioni di ogni documento
                 * risulterà ordinata
                 */
                for(Entry<Map<String, Object>,Map<String,Double>> documentoCorrente : listaDocumenti) {
                    infoDocumenti.add(documentoCorrente.getKey());
                }
                listaDocumenti=null;
                
                if(visualizzaListaDocumenti) {
                    
                    for (int i = 0; i < infoDocumenti.size(); i++) {
                        //i: N° di riga
                        Vector<Object> infoRiga=new Vector<Object>();
                        Map<String,Object> infoDocumento=infoDocumenti.get(i);
                        /**
                         * Di ogni documento del training set viene visualizzata la distanza (secondo la metrica corrente) e, nel caso la metrica sia
                         * la TF-IDF, anche il valore dell'angolo tra il vettore corrispondente ad ogni documento ed il vettore corrispondente alla query
                         */
                        infoRiga.add(String.format("%6.5f",infoDocumento.get("Distanza")) + (metrica.equals("TF-IDF") ? " ("+String.format("%4.2f",infoDocumento.get("Angolo"))+"°)" : ""));
                        infoRiga.add(infoDocumento.get("Categorie"));
                        infoRiga.add(infoDocumento.get("Titolo"));
                        infoRiga.add(infoDocumento.get("Percorso"));
                        tableModel.addRow(infoRiga);
                    }
                }
                /**
                 * Il risultato finale del task, da restituire ai chiamanti del metodo {@link get}, è la lista
                 * delle informazioni sui documenti del training set, ordinata in base alla distanza (secondo
                 * la metrica corrente) di ogni documento dalla query.
                 */
                return infoDocumenti;
            } catch (Exception ex) {
                failed(ex);
                return null;
            }
        }
        
        /**
         * Se il task ha raggiungo la fine del suo flusso di esecuzione senza errori-->
         * Si trova nello stato 'Succeeded'-->Viene eseguito questo metodo, a cui viene passato
         * in input il risultato della computazione (la lista con le informazioni su ogni documento
         * del training set, ordinata in base alla distanza rispetto alla query corrente).
         * Viene quindi determinata e visualizzata la categoria del documento query corrente, applicando
         * l'algoritmo del K-NN al risultato della computazione
         * 
         * @param result        La lista con le informazioni su ogni documento del training set,
         *                      ordinata in base alla distanza rispetto alla query corrente
         */
        @Override
        protected void succeeded(LinkedList<Map<String,Object>> result) {
            // Runs on the EDT.  Update the GUI based on
            // the result computed by doInBackground().
            String categoria=determinaCategoria(result,KNN);
            setMessage("Categoria stimata: '"+categoria + "'");
        }
        
       /**
        * Se invece il task non ha completato la sua esecuzione con successo-->
        * Si trova nello stato 'Failed'-->Viene mostrato un messaggio di errore.
        * 
        * @param ex             Un'istanza della classe {@link Throwable} che indica
        *                       l'errore che ha portato allo stato 'Failed'
        */
        @Override
        protected void failed(Throwable ex) {
            mostraMessaggioErrore(ex.toString());
            ex.printStackTrace();
        }
        
        /**
         * In ogni caso, qualunque sia lo stato finale del task alla fine del suo
         * ciclo di vita, questo metodo viene eseguito per ultimo.
         * In questa implementazione, setta il valore della proprietà 'taskRunning'
         * a "false", dato che il task si è concluso (con successo o meno)
         */
        @Override
        protected void finished() {
            setTaskRunning(false);
        }
        
    }
    
    /**
     * Determina la categoria di appartenenza del documento query, utilizzando l'algoritmo K-NN
     * 
     * @param   infoDocumenti   La lista con le informazioni sui documenti del training set, ordinata in base alla distanza
     *                          dal documento query
     * @param   k               Valore di K-->I primi K documenti presenti nella lista dei documenti verrano esaminati per
     *                          determinare la categoria più rappresentativa, quella cioè di cui sono presenti più documenti.
     *                          Nel caso ci siano due o più categorie con la stessa frequenza, viene scelta quella che appare
     *                          per prima nella lista, quella cioè che ha il 1° documento meno distante dalla query
     * @return                  Una stringa contenente il nome della categoria determinata con il K-NN
     */
    private String determinaCategoria(LinkedList<Map<String, Object>> infoDocumenti, int k) {

        /**
         * Come struttura dati di supporto viene utilizzata una tabella hash indicizzata sui nomi delle categorie
         * (quindi stringhe), ed avente come valori coppie di interi che rappresentano rispettivamente:
         * 1 - La distanza minima dal documento query di uno dei documenti appartenenti alla categoria della
         *     chiave, che compaiono tra i primi k della lista
         * 2 - La frequenza della categoria rappresentata dalla chiave
         */
        Map<String, Entry<Integer, Integer>> FrequenzaCategorie = new HashMap<String, Entry<Integer, Integer>>();
        Set<String> categorieDocumentoCorrente;
        int frequenzaMax, frequenzaCorrente;
        Entry<Integer, Integer> entryCorrente;
        Entry<String, Entry<Integer, Integer>> maxEntry = null;
        int numeroCategorieEsaminate=0;
        boolean esci=false;
        for (int i = 0; i < k; i++) {
            categorieDocumentoCorrente=(Set<String>) infoDocumenti.get(i).get("Categorie");
            for(String categoriaCorrente : categorieDocumentoCorrente) {
                numeroCategorieEsaminate++;
                entryCorrente = (SimpleEntry<Integer, Integer>) FrequenzaCategorie.get(categoriaCorrente);
                if (entryCorrente == null) {
                    entryCorrente = new AbstractMap.SimpleEntry<Integer,Integer>(i, 0);
                }
                entryCorrente.setValue(entryCorrente.getValue() + 1);
                //La frequenza della categoria viene incrementata di 1
                FrequenzaCategorie.put(categoriaCorrente, entryCorrente);
                
                if (i == 0) {
                    /**
                     * Alla 1a iterazione maxEntry viene inizializzata con la chiave della
                     * categoria presente nella 1a riga, ed il valore della coppia <0,1>
                     */
                    maxEntry = new AbstractMap.SimpleEntry<String,Entry<Integer,Integer>>(categoriaCorrente, entryCorrente);
                }
                /** 
                 * L'assegnamento sottostanza, 'maxEntry.getValue()', legge la mappa <Distanza minima,Frequenza>,
                 * da cui a sua volta viene letto il valore 'Frequenza'
                 */
                frequenzaMax = maxEntry.getValue().getValue();
                frequenzaCorrente = entryCorrente.getValue();
                if (frequenzaCorrente > frequenzaMax ||
                        (frequenzaCorrente == frequenzaMax && entryCorrente.getKey() < maxEntry.getValue().getKey())) {
                    /**
                     * La condizione di questo 'if' significa: Se la frequenza della categoria del documento corrente è
                     * maggiore della frequenza massima corrente, oppure se è uguale ma la categoria corrente ha un documento
                     * più simile alla query rispetto alla categoria più frequente al momento-->Cambia la categoria più frequente
                     * (che poi verrà assegnata alla query) con quella corrente
                     */
                    maxEntry = new AbstractMap.SimpleEntry<String,Entry<Integer,Integer>>(categoriaCorrente, entryCorrente);
                }
            }
            if(esci) {
                break;
            }
            /**
             * Se il prossimo documento esaminato appartiene a tante di quelle categorie da far raggiungere o superare
             * il limite di k categorie esaminate-->La prossima iterazione del ciclo sarà l'ultima
             */
            if(i<=infoDocumenti.size()-2 && numeroCategorieEsaminate+((Set<String>)infoDocumenti.get(i+1).get("Categorie")).size()>=k) {
                esci=true;
            }
        }
        /**
         * Alla fine del ciclo in maxEntry sono presenti:
         * 1 - Chiave: Il nome della categoria maggiormente rappresentata nel k-insieme
         * 2 - Valore: La coppia <Riga iniziale, Frequenza> relativa a tale categoria, con frequenza massima, e riga iniziale minima
         */
        return maxEntry.getKey();
    }
    
    /**
     * Azione, e task di background associato, per effettuare la K-Fold cross validation su tutte le pagine del training set,
     * allo scopo di determinare il valore ottimale di K per il K-NN.
     * Il task blocca la finestra corrente, e mostra automaticamente un JOptionPane che, assieme all'apposita area di testo
     * nella finestra principale, informa l'utente sullo stato della computazione
     *  
     * @return                  Riferimento al task di backgroud associato all'azione
     */
    @Action(block = Task.BlockingScope.WINDOW)
    public Task Validation() {
        
        /*TrainingDialog trainingDialog=new TrainingDialog(getFrame());
        trainingDialog.setVisible(true);
        int KNNMaximum=trainingDialog.getKNNMaximum();
        trainingDialog.dispose();*/
        
        return new ValidationTask(getApplication(),application.getMaximumKNNValidation(false));
    }
    
    private class ValidationTask extends org.jdesktop.application.Task<Entry<Integer,Double>, Void> {
        
        int KNNMinimum,KNNMaximum;
        boolean isStratificato,isLogging;
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
                this.isStratificato = application.isStratificato(false);
                this.isLogging=application.isLogging(false);
                if(isLogging) {
                    /**
                     * Se il logging durante la fase di validazione è abilitato, è necessario
                     * aprire il file di log (con il nome e la modalità specificati tramite il
                     * pannello delle preferenze), e creare un oggetto di tipo {@link Logger} per
                     * registrare i messaggi di tipo 'INFO'.
                     */
                    FH = new FileHandler(application.getLogFile(false), !application.isOverwriteLogFile(false));
                    FH.setLevel(Level.INFO);
                    FH.setFormatter(new SimpleFormatter());
                    infoLogger = Logger.getLogger(DocumentClassifierView.class.getName());
                    for (Handler currentHandler : infoLogger.getHandlers())
                    {
                        infoLogger.removeHandler(currentHandler);
                    }
                    infoLogger.addHandler(FH);
                }
            } catch (Exception ex) {
                mostraMessaggioErrore(ex.getMessage());
                ex.printStackTrace();
                cancel(false);
            }
        }
        
        @Override
        protected Entry<Integer,Double> doInBackground() throws InterruptedException, ExecutionException {
            // Your Task's code here.  This method runs
            // on a background thread, so don't reference
            // the Swing GUI from here.
            
            /**
             * K-fold cross validation: Definito il valore di K per il K-fold, ripetere in un ciclo queste operazioni
             * 1 - Prepara un partizionamento (casuale o stratificato) del training set in 'K-Fold' sottoinsiemi
             * 2 - Per il 1° valore di K-NN:
             *  2.1 - Per ogni sottoinsieme del training set partizionato:
             *      2.1.1 - Eliminarlo temporaneamente dal training set ed utilizzarlo come control set
             *      2.1.2 - Per ogni documento del control set (di cui si conosce già la categoria di appartenenza), utilizzarlo come query corrente
             *          2.1.2.1 - Eseguire il task 'OrdinaDocumenti', e salvare la lista con le informazioni sui documenti del training set, ordinati
             *                    rispetto alla distanza con la query corrente, per uso futuro, in un'apposita mappa che associ ad ogni documento
             *                    la propria lista
             *          2.1.2.2 - Confrontare la categoria restituita con quella effettiva, ed in caso di mismatch incrementare il contatore dell'errore
             *                    di classificazione
             *  2.2 - Calcolare l'errore di classificazione medio per il valore corrente di K-NN, e memorizzarlo in un'apposita mappa
             * 3 - Per i successivi valori di K-NN, dal 2° fino al valore massimo scelto dall'utente, non è necessario eseguire il punto 2.1.2.1, ma basta
             *     classificare la query corrente utilizzando la lista con le informazioni sui documenti salvata in precedenza
             * 4 - Scegliere il valore di K a cui corrisponde l'errore medio minimo
             */
            /**
             * La variabile 'startTime' contiene il tempo d'inizio della fase di validazione. E' utilizzata per calcolare il tempo totale impiegato da questa fase.
             */
            startTime=Calendar.getInstance().getTimeInMillis();
            fieldURL.setText("");
            labelTitolo.setText("");
            labelTesto.setText("");
            textAreaOutput.setText("");
            setDocumentoLetto(false);
            int KFold=application.getKFold(false);
            
            /**
             * La struttura dati 'erroreMinimo' rappresenta una entry singola di una mappa, che alla fine della fase di validazione
             * contiene come chiave il valore di K (riferito al K-NN) per cui l'errore di classificazione medio (inteso come media
             * dell'errore di classificazione su ogni fold) è minimo, e come valore il valore di tale errore
             * Chiave: K t.c. l'errore di classificazione medio (su tutte le possibili combinazioni training set+control set) è minimo
             * Valore: Errore di classificazione medio minimo corrispondente
             */
            Entry<Integer,Double> erroreMinimo = new AbstractMap.SimpleEntry<Integer,Double>(0, Double.MAX_VALUE);
            /**
             * La mappa che contiene tutte le associazioni 'Valore di K (Integer)'->'Errore di classificazione medio corrispondente (Double)'
             */
            Map<Integer, Double> mappaErroriMedi = new HashMap<Integer, Double>();
            /**
             * Viene creata una copia del training set. Ciò è necessario perchè nel caso il partizionamento sia stratificato, ogni fold verrà
             * creato iterando su ogni categoria del training set, ed eliminando da essa ogni documenti aggiunto al fold corrente.
             * Poichè quindi il training set è immutabile, e comunque non deve essere modificato, viene creata una sua copia temporanea
             */
            Set<Set<Documento>> copiaTrainingSet=new HashSet<Set<Documento>>();
            for (Set<Documento> sottoInsiemeCorrente : application.getTrainingSet() ) {
                Set<Documento> copiaSottoInsiemeCorrente=new HashSet<Documento>();
                copiaSottoInsiemeCorrente.addAll(sottoInsiemeCorrente);
                copiaTrainingSet.add(copiaSottoInsiemeCorrente);
            }
            
            Iterator iteratoreCasualeTrainingSet=null;
            int numeroCategorie=copiaTrainingSet.size();
            //Dimensione di ogni fold
            int dimensioneFold=application.getTrainingSetSize()/KFold;
            int numeroDocumentiPerCategoria=0;
            if(isStratificato) {
                dimensioneFold = (dimensioneFold/numeroCategorie) * numeroCategorie;
                numeroDocumentiPerCategoria=dimensioneFold/numeroCategorie;
            } else {
                Set<Documento> trainingSetNormalizzato=new HashSet<Documento>();
                for(Set<Documento> sottoInsiemeCorrente: copiaTrainingSet) {
                    trainingSetNormalizzato.addAll(sottoInsiemeCorrente);
                }
                iteratoreCasualeTrainingSet=trainingSetNormalizzato.iterator();
            }
            
            /**
             * Prepara un partizionamento (casuale o stratificato) del training set (una sua copia) in K-Fold sottoinsiemi
             */
            Set<Set<Documento>> trainingSetPartizionato=new HashSet<Set<Documento>>();
            Set<Documento> sottoInsiemeTrainingSet;
            Iterator iteratoreCategoriaTrainingSet;
            for(int j=0;j<KFold;j++) {                                          //1
                /**
                 * Vengono creati K-Fold sottoinsiemi, tutti della stessa dimensione
                 */
                sottoInsiemeTrainingSet=new LinkedHashSet<Documento>();
                if(isStratificato) {
                    for(Set<Documento> categoria: copiaTrainingSet) {
                        iteratoreCategoriaTrainingSet=categoria.iterator();
                        for(int z=0;z<numeroDocumentiPerCategoria;z++) {
                            sottoInsiemeTrainingSet.add((Documento) iteratoreCategoriaTrainingSet.next());
                            iteratoreCategoriaTrainingSet.remove();
                        }
                    }
                } else {
                    for(int z=0;z<dimensioneFold;z++) {
                        sottoInsiemeTrainingSet.add((Documento) iteratoreCasualeTrainingSet.next());
                    }
                }
                trainingSetPartizionato.add(sottoInsiemeTrainingSet);
            }
            
            /**
             * Il numero totale di iterazioni viene calcolato a partire da K-NN=2
             */
            final int numTotaleIterazioni=(KNNMaximum-(KNNMinimum+1)+1)*dimensioneFold*KFold;
            int numIterazioneCorrente=0;
            int indiceDocumentoCorrente;
            int indiceFoldCorrente;
            int contatoreErrori;
            String categoriaCalcolata;
            Set<String> categorieEffettive;
            Set<Set<Documento>> trainingSetCorrente;
            String isStratificato=(application.isStratificato(false)) ? "stratificato" : "non stratificato";
            boolean ordinaDocumenti;
            /**
             * La seguente mappa 'infoDocumentoOrdinati' serve per salvare i risultati dell'ordinamento dei documenti del training set
             * corrente rispetto alla query corrente durante la 1a iterazione della fase di validazione, per poterli riutilizzare nelle
             * fasi successive
             */
            Map<Documento,LinkedList<Map<String, Object>>> infoDocumentiOrdinati=new HashMap<Documento, LinkedList<Map<String, Object>>>();
            for (int i = KNNMinimum; i <= KNNMaximum; i++) {
                /**
                 * La variabile booleana 'ordinaDocumenti' indica se l'iterazione corrente è la 1a della fase di validazione,
                 * e se quindi bisogna effettivamente effettuare il ranking dei documenti del training set corrente rispetto
                 * alla query corrente, oppure se è sufficiente utilizzare i risultati salvati della 1a iterazione
                 */
                ordinaDocumenti=(i==KNNMinimum);
                String currentMessage="K-NN: "+i+"/"+KNNMaximum+" - K-Fold: "+KFold+" [Partizionamento "+isStratificato+"] \n";
                if(ordinaDocumenti) {
                    currentMessage += "(Ranking ed ordinamento documenti training set in corso...)";
                }
                setMessage(currentMessage);
                textAreaOutput.append(currentMessage+"\n");
                if(isLogging) {
                    /**
                     * Se il logging è abilitato, registro sul file e sulla console il messaggio corrente
                     */
                    infoLogger.info(currentMessage);
                }
                
                indiceFoldCorrente=0;
                contatoreErrori=0;
                for ( Set<Documento> controlSetCorrente : trainingSetPartizionato ) {   //2.1
                    
                    indiceFoldCorrente++;
                    trainingSetCorrente=new HashSet<Set<Documento>>(trainingSetPartizionato);
                    trainingSetCorrente.remove(controlSetCorrente);             //2.1.1
                    indiceDocumentoCorrente=0;
                    for(Documento queryCorrente: controlSetCorrente) {          //2.1.2
                        indiceDocumentoCorrente++;
                        if(!ordinaDocumenti) {
                            numIterazioneCorrente++;
                            setProgress(numIterazioneCorrente,1,numTotaleIterazioni);
                        }
                        
                        if(application.isVisualizzaDocumentoCorrente(false)) {
                            /**
                             * Se la visualizzazione del documento corrente è attivata,
                             * la sua URL, il titolo ed il testo vengono visualizzati
                             * nell'interfaccia grafica
                             */
                            fieldURL.setText(queryCorrente.getPercorso());
                            labelTitolo.setText(queryCorrente.getTitolo());
                            labelTesto.setText(queryCorrente.getTesto());
                        }
                        
                        if(ordinaDocumenti) {                                   //2.1.2.1
                            taskCorrente = new OrdinaDocumentiTask(getApplication(), queryCorrente, trainingSetCorrente, i, application.isVisualizzaListaDocumenti(false));
                            taskCorrente.execute();
                            infoDocumentiOrdinati.put(queryCorrente,((OrdinaDocumentiTask)taskCorrente).get());
                            taskCorrente=null;
                        }
                        categoriaCalcolata = determinaCategoria(infoDocumentiOrdinati.get(queryCorrente), i);
                        categorieEffettive = queryCorrente.getCategorie();
                        if (!categorieEffettive.contains(categoriaCalcolata)) { //2.1.2.2
                            contatoreErrori++;
                        }
                        currentMessage="Notizia n° "+indiceDocumentoCorrente+"/"+dimensioneFold+" (fold n° "+indiceFoldCorrente+"): "+"Categoria stimata: '"+categoriaCalcolata+"', Categorie effettive: '"+categorieEffettive+"'"+" - Numero di errori corrente: "+contatoreErrori;
                        textAreaOutput.append(currentMessage+"\n");
                        if(isLogging) {
                            infoLogger.info(currentMessage);
                        }
                        categorieEffettive=null;
                    }
                    trainingSetCorrente=null;
                }
                double erroreClassificazione=((double)contatoreErrori/(KFold*dimensioneFold))*100;    //2.2
                currentMessage="Errore di classificazione per K-NN="+i+" e K-Fold="+KFold+": "+erroreClassificazione+"%";
                textAreaOutput.append(currentMessage+"\n");
                if(isLogging) {
                    infoLogger.info(currentMessage);
                }
                
                mappaErroriMedi.put(i, erroreClassificazione);
                
                if (erroreClassificazione < erroreMinimo.getValue()) {          //4
                    erroreMinimo = new AbstractMap.SimpleEntry<Integer,Double>(i, erroreClassificazione);
                }
                //setProgress(i,KNNMinimum,KNNMaximum);
            }
            return erroreMinimo;
        }
        
        /**
         * Se il task ha raggiungo la fine del suo flusso di esecuzione senza errori-->
         * Si trova nello stato 'Succeeded'-->Viene eseguito questo metodo, a cui viene passato
         * in input il risultato della computazione (un'istanza della classe 'Entry<Integer,Double>'
         * contenente il valore ottimale di K per il K-NN, e l'errore di classificazione ad esso relativo).
         * Viene quindi visualizzato un opportuno messaggio di stato, ed il valore di K-NN viene
         * automaticamente settato al valore ottimale
         * 
         * @param result        Un'istanza della classe 'Entry<Integer,Double>' contenente il valore ottimale
         *                      di K per il K-NN, e l'errore di classificazione ad esso relativo
         */
        @Override
        protected void succeeded(Entry<Integer,Double> result) {
            try {
                // Runs on the EDT.  Update the GUI based on
                // the result computed by doInBackground().
                String currentMessage = "Il valore ottimale di K-NN è " + result.getKey() + " (Errore di classificazione: " + result.getValue() + "%)";
                setMessage(currentMessage);
                textAreaOutput.append(currentMessage+"\n");
                if(isLogging) {
                    infoLogger.info(currentMessage);
                }
                stopTime = Calendar.getInstance().getTimeInMillis();
                long tempoImpiegato = stopTime - startTime;
                textAreaOutput.append("Tempo totale impiegato: " + tempoImpiegato + " millisecondi");
                application.setKNN(result.getKey());
            } catch (Exception ex) {
                mostraMessaggioErrore(ex.getMessage());
                ex.printStackTrace();
            }
        }
        
        /**
         * Se il task viene cancellato in corso di esecuzione-->Il messaggio di stato viene settato alla stringa vuota
         */
        @Override
        protected void cancelled() {
            setMessage("");
        }
        
       /**
        * Se invece il task non ha completato la sua esecuzione con successo-->
        * Si trova nello stato 'Failed'-->Il messaggio di stato viene settato alla stringa vuota.
        * 
        * @param ex             Un'istanza della classe {@link Throwable} che indica
        *                       l'errore che ha portato allo stato 'Failed'
        */
        @Override
        protected void failed(Throwable ex) {
            cancelled();
        }
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aiutoMenuItem;
    private javax.swing.JMenuItem apriMenuItem;
    private javax.swing.JButton bottoneApri;
    private javax.swing.JButton bottoneCerca;
    private javax.swing.JButton bottoneLeggi;
    private javax.swing.JButton bottoneStop;
    private javax.swing.JButton bottoneValidation;
    private javax.swing.JTextField fieldURL;
    private javax.swing.ButtonGroup gruppoMetriche;
    private javax.swing.JTextArea labelTesto;
    private javax.swing.JLabel labelTitolo;
    private javax.swing.JTable listaDocumentiCorrelati;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem menuItemPreferenze;
    private javax.swing.JMenu modificaMenu;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JScrollPane scrollPaneLabelTesto;
    private javax.swing.JScrollPane scrollPanePagineCorrelate;
    private javax.swing.JScrollPane scrollPaneTextAreaOutput;
    private javax.swing.JScrollPane scrollPanelLabelTitolo;
    private javax.swing.JToolBar.Separator separatorApriTraining;
    private javax.swing.JToolBar.Separator separatorIniziale;
    private javax.swing.JSplitPane splitPaneOrizzontale;
    private javax.swing.JSplitPane splitPaneVerticale;
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