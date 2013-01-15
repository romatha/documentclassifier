package documentclassifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import org.tartarus.snowball.SnowballProgram;

/**
 * This class represents a document, and contains a title, a text, the list of categories it belongs to,
 * the URL (local or remote) from where it is possible to retrieve the original, and the histogram of its
 * terms.
 *
 * @author      Salvo Danilo Giuffrida (salvod.giuffrida@gmail.com)
 */
class Document {

    private String title,  text,  path;
    /**
     * A document can also belong to more than one category, of copies of it are present in multiple
     * subdirectories of the training set. To model this situation, the categories a document belongs
     * to are represented as strings inside a set (class Set<String> of the Collections framework).
     */
    private Set<String> categories;
    /**
     * The histogram of each document is represented as a map, having as keys the strings of its unique terms,
     * and as values the relative frequency of each term inside the document.
     */
    private Map<String, Double> histogram;

    /**
     * This constructor creates a new document with the title, text, initial category, and URL specified as
     * input parameters.
     * <p>
     * Later on, it will be possible to add more categories to it, using the method {@link #addCategory addCategory}.
     * 
     * @param title             The title of the document.
     * @param text              The complete text of the document.
     * @param initialCategory   One of the categories the document belongs to.
     * @param path              The URL (local or remote) from where it is possible to read the original document file.
     */
    public Document(String title, String text, String initialCategory, String path) {
        try {
            this.title = title;
            this.text = text;
            this.categories = new HashSet<String>();
            categories.add(initialCategory);
            this.path = path;
            /**
             * When the document is created, the histogram of its terms is generated, and this it will be
             * be used by the current metric, to calculate the distance between this document and the
             * query.
             * In case the user decides to enable/disable stemming, and/or the removal of stopwords
             * from the document, the entire training set is regenerated, because the histogram of each
             * document must be recalculated, since terms that before were deleted during pre-processing
             * (like stopwords), now should instead be taken into consideration, or, analogously, terms that
             * before were stemmed to their common root, now must be considered as different.
             */
            this.histogram = createHistogram();
        } catch (Exception ex) {
            DocumentClassifierView.showErrorMessage(ex.toString());
        }
    }

    /**
     * This constructor creates a new document without any category.
     * It is used to create an instance of this class representing the current query document.
     * whose category we don't know.
     * 
     * @param title             The document's title
     * @param text              The document's text
     * @param path              The URL (local or remote) from where it is possible to read the original document file
     * 
     */
    public Document(String title, String text, String path) {
        this(title, text, null, path);
    }

    /**
     * This constructor creates a new document with only the title.
     * It is used to create an instance representing a query, like in a search engine, from whom the most relevant documents respect
     * to it must be retrieved.
     * 
     * @param   query            The query to send to the information retrieval system.
     */
    public Document(String query) {
        this(query, "", null);
    }

    /**
     * This method creates the histogram of the document represented by this instance; if necessary the text
     * is pre-processed (removing stopwords and stemming the remaining terms) before calculating the frequency
     * of each one of its terms.
     * 
     * @return                  The histogram of the terms of the document, represented by a map that has
     *                          keys (the terms) of type {@link String}, and values (their relative frequencies)
     *                          of type {@link Double}.
     */
    private Map<String, Double> createHistogram() throws Exception {

        DocumentClassifierApp application = DocumentClassifierApp.getApplication();
        boolean isStemming = application.isStemming(false);
        boolean isRemovalStopWords = application.isRemovalStopWords(false);

        /**
         * Even if the user has chosen (through the Preferences panel) not to apply stemming to
         * document terms, it is necessary to create the classes that do it, otherwise the compiler
         * will generate an error.
         */
        Class stemClass = Class.forName(application.getStemmer(false));
        SnowballProgram stemmer = (SnowballProgram) stemClass.newInstance();
        @SuppressWarnings("unchecked")
        Method stemMethod = stemClass.getMethod("stem", new Class[0]);

        Object[] emptyArgs = new Object[0];
        String specialCharacters = " \t\n\r\f,;.:!'\"()?[]=-@";

        Map<String, Double> documentHistogram = new HashMap<String, Double>();
        String currentToken;
        Double frequency;
        int weight;
        String row;

        /**
         * I build the set of stopwords, by reading the appropriate file.
         */
        Set<String> stopWordsList = new HashSet<String>();
        BufferedReader stopWordsBR;
        File stopWordsListFile = new File(DocumentClassifierApp.getApplication().getStopWordsList(false));
        String[] fields;
        if (isRemovalStopWords) {
            /**
             * If the user has chosen not to enable the removal of stopwords, no element is added
             * to the set, which remains empty.
             */
            stopWordsBR = new BufferedReader(new FileReader(stopWordsListFile));
            while ((row = stopWordsBR.readLine()) != null) {
                if (!row.isEmpty()) {
                    fields = row.split("|");
                    if (!fields[0].startsWith(" ")) {
                        stopWordsList.add(fields[0].trim());
                    }
                }
            }
            stopWordsBR.close();
        }

        /**
         * Pre-processing of the text.
         * The title and text of the document are represented as two strings, belonging to an array,
         * such that I will be able to apply the same operations to both of them, but weighting
         * the terms in a different way depending if they belong to the title or the text.
         */
        String[] titleText = new String[2];
        titleText[0] = title;
        titleText[1] = text;
        for (int j = 0; j <= 1; j++) {
            if (j == 0) {
                /**
                 * If I'm reading the title of the document-->Its terms have a double weight than the terms
                 * of the text, because they are more directly related to the argument and context of the document,
                 * than the terms of the text.
                 */
                weight = 2;
            } else {
                weight = 1;
            }
            titleText[j] = titleText[j].toLowerCase();
            StringTokenizer ST = new StringTokenizer(titleText[j], specialCharacters);
            while (ST.hasMoreTokens()) {
                currentToken = ST.nextToken();
                /**
                 * Removal of stopwords (if enabled by the user) and of numbers (in any case).
                 * The word 'removal' is inappropriate, because what is really done is simply to not take
                 * into consideration a term if it is present in the stopwords list, or if it represents
                 * a number. In these two cases the term is not added to the map which represents the
                 * histogram of the document.
                 */
                if (!stopWordsList.contains(currentToken) && !currentToken.matches("\\d+")) {

                    if (isStemming) {
                        /**
                         * Stemming of the current term: The stemmer creates a new term containing the root
                         * of the one given in input.
                         */
                        stemmer.setCurrent(currentToken);
                        stemMethod.invoke(stemmer, emptyArgs);
                        currentToken = stemmer.getCurrent();
                    }
                    /**
                     * The frequency of the current term (eventually stemmed to its root) is read from the
                     * document's histogram, and updated (depending on the weight assigned to the current
                     * term).
                     */
                    frequency = documentHistogram.get(currentToken);
                    if (frequency == null) {
                        frequency = 0.0;
                    }
                    documentHistogram.put(currentToken, frequency + weight);
                }
            }
        }

        /**
         * The histogram has been completed-->Now it is necessary to normalize its frequencies
         * to the length of the document, making them relative.
         */
        double normalizationFactor = 0;
        for (double I : documentHistogram.values()) {
            //Calculation of the document's length after pre-processing
            normalizationFactor += I;
        }
        //Normalization of frequencies (from absolute to relative)
        for (String Token : documentHistogram.keySet()) {
            documentHistogram.put(Token, documentHistogram.get(Token) / normalizationFactor);
        }
        /**
         * I make sure that, once created, the histogram of this document can't be modified anymore
         * (accidentally or intentionally) by other classes that read it.
         */
        return Collections.unmodifiableMap(documentHistogram);
    }

    /**
     * Accessor method to read the document's histogram.
     * 
     * @return                  The document's histogram.
     */
    public Map<String, Double> getHistogram() {
        return histogram;
    }

    /**
     * Accessor method to read the set of categories this document belongs to.
     * 
     * @return                  Set with the names of categories this document belongs to.
     */
    public Set<String> getCategories() {
        return categories;
    }

    /**
     * This method adds the name of a category to the set.
     * 
     * @param category         The name of the category to add.
     */
    public void addCategory(String category) {
        categories.add(category);
    }

    /**
     * This accessor method returns the URL from where it is possible to get the original document (file PDF, HTML, ecc...)
     * represented by this instance.
     * 
     * @return                  The URL (local or remote) from where it is possible to get
     *                          the original document represented by this instance.
     */
    public String getPath() {
        return path;
    }

    /**
     * Accessor method to read the text of the document represented by this instance.
     * 
     * @return                  The text of the document represented by this instance.
     */
    public String getText() {
        return text;
    }

    /**
     * Accessor method to read the title of the document represented by this instance.
     * 
     * @return                  The title of the document represented by this instance.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Two documents are considered equals if they have both the same title and the same text.
     * 
     * @param obj
     * @return                  A boolean value indicating if the document provided in input is equal
     *                          to the one represented by this instance.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Document other = (Document) obj;
        if (this.title != other.title && (this.title == null || !this.title.equals(other.title))) {
            return false;
        }
        if (this.text != other.text && (this.text == null || !this.text.equals(other.text))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + (this.title != null ? this.title.hashCode() : 0);
        hash = 71 * hash + (this.text != null ? this.text.hashCode() : 0);
        return hash;
    }
}
