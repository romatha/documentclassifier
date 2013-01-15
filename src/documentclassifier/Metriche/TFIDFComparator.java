
package documentclassifier.Metriche;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * This class calculates the values of the metric TF-IDF (Term Frequency-Inverse Document Frequency),
 * that must be used in the VSM (Vector Space Model) to rank documents of the training set.
 * <p>
 * It implements the interface {@link Comparator}, on elements of type Entry<Map<String,Object>, Map<String, Double>>,
 * each one containing information on single documents of the training set.
 * Using the information present in each entry, the Comparator orders the training set's documents depending on the cosine
 * of the angle between each one of them and the query document.
 * 
 * @author      Salvo Danilo Giuffrida (salvod.giuffrida@gmail.com)
 * @see         Comparator
 */
public class TFIDFComparator implements Comparator<Entry<Map<String,Object>, Map<String, Double>>> {
    
    /**
     * Method that implements the interface {@link Comparator}, to compare two documents
     * whose TF-IDF values (for each term) have been calculated.
     * <p>
     * The TF-IDF values, and the cosines, have been calculated previously, using the static method
     * {@link #calculateCosine calculateCosine}.
     * <p>
     * The comparation then is done calculating the cosine of the angle between each document and
     * the query, and returning in accordance the value 1, 0 or -1.
     * <p>
     * This method gets called for each possible pair of documents in the training set.
     * <p>
     * 
     * @param A             The 1st document to compare.
     * @param B             The 2nd document to compare.
     * @return              An integer between these 3 possible values: -1, 0 or 1, representing the ordering
     *                      between the 1st and the 2nd input document.
     */
    public int compare(Entry<Map<String,Object>, Map<String, Double>> A, Entry<Map<String,Object>, Map<String, Double>> B) {
        
        /**
         * The first thing that is done, is to read the value of the cosine of each of the two documents from the query,
         * calculated previously with the static method {@link #calculateCosine calculateCosine}, and inserted in the map
         * representing the key of each entry (which contains information on the document it refers to), at the voice
         * "Distance".
         */
        double cosA = (Double) A.getKey().get("Distance");
        double cosB = (Double) B.getKey().get("Distance");
        if (cosA > cosB) {
            return -1;
        } else if (cosA == cosB) {
            return 0;
        } else {
            return 1;
        }
    }
    
    /**
     * Static method used to calculate the cosine of the angle between each document of a set (represented by
     * a vector that for each document's term contains its relative frequency), and a reference document (query).
     * 
     * @param trainingSet   A set of documents, represented with a list of instances of the class Entry<Map<String,Object>, Map<String, Double>>,
     *                      whose key is a map {@link String}->{@link Object}, containing information on a document, and whose value is a map
     *                      {@link String}->{@link Double}, which represents the histogram of terms of the document (after pre-processing).
     * @param query         The histogram of the query document from where all cosines are calculated.
     *                      It is implemented by a map, that associates keys of type {@link String} to elements of type {@link Double}.
     */
    public static void calculateCosine(LinkedList<Entry<Map<String, Object>, Map<String, Double>>> trainingSet, Map<String, Double> query) {
        
        /**
         * Before calculating all cosines, it is necessary to calculate the TF-IDF value of each term of each histogram of the document set
         * (training set+query document).
         * The 'TF' part of the formula, that is the relative frequency of each term inside each document, has already been calculated during
         * the histogram's creation phase, everytime an instance of the class {@link Documento} was created.
         * N.B.: The query document must temporarly be part of the training set.
         */
        
        /**
         * The first thing that is done, is to create an instance of the class 'Entry<Map<String,Object>, Map<String, Double>>',
         * that represents the query document, in order to temporarly add it to the training set.
         */
        Entry<Map<String,Object>, Map<String, Double>> entryQuery=new AbstractMap.SimpleEntry<Map<String,Object>, Map<String, Double>>(null,query);
        trainingSet.add(entryQuery);
        double TFIDF;
        /**
         * A copy of the training set provided in input is created: It will memorize the histograms of each document,
         * but updated with the values of each term's 'TF-IDF', instead than the relative frequency.
         * This is necessary to avoid overwriting permanently the original histograms of the training set's documents,
         * something that would bring wrong results in output, different at each invocation of the classification task.
         */
        List<Entry<Map<String,Object>,Map<String,Double>>> copyTrainingSet=new LinkedList<Entry<Map<String, Object>, Map<String, Double>>>();
        Map<String, Double> currentDocumentHistogram;
        /**
         * The map named 'DF' must be used to memorize, as the name suggest, the 'DF' part of each term 'j' in the training set (DFj).
         * This value is calculated the first time that a certain term is found in one of the histograms of the training set, and from
         * that point on, the program will just need to access it to get DFj, instead than performing a redundant calculation.
         */
        Map<String, Integer> IDF = new HashMap<String, Integer>();
        Integer absFrequencyCurrentTerm;
        /**
         * The variable 'totalNumberDocuments' contains the dimension of the document set (N).
         */
        int totalNumberDocuments = trainingSet.size();
        /**
         * For each document of the document set...
         */
        for(Entry<Map<String,Object>,Map<String,Double>> currentEntry : trainingSet) {
            
            /**
             * A copy of its histogram is created, and this will be modified by substituting, for each term,
             * the value of relative frequency with the value of TF-IDF.
             */
            currentDocumentHistogram=new HashMap<String, Double>();
            currentDocumentHistogram.putAll(currentEntry.getValue());
            for (String Token : currentDocumentHistogram.keySet()) {
                if (IDF.get(Token) == null) {
                    /**
                     * The current term (j) has never been met before-->Its Document Frequency (DFj) is not
                     * yet present in the map-->The number of occurences of this term in the training set
                     * is counted, divided by its dimension, and memorized in the map.
                     * In this way, from the next time on, it will be sufficient to access the map (in constant time)
                     * to know DFj, avoiding unnecessary recalculations.
                     */
                    absFrequencyCurrentTerm = 0;
                    for (int j = 0; j < totalNumberDocuments; j++) {
                        if (trainingSet.get(j).getValue().get(Token) != null) {
                            absFrequencyCurrentTerm++;
                        }
                    }
                    IDF.put(Token, totalNumberDocuments / absFrequencyCurrentTerm);
                }
                TFIDF = currentDocumentHistogram.get(Token) * Math.log10(IDF.get(Token));
                currentDocumentHistogram.put(Token, TFIDF);
            }
            copyTrainingSet.add(new AbstractMap.SimpleEntry<Map<String,Object>,Map<String,Double>>(currentEntry.getKey(),currentDocumentHistogram));
            if(currentEntry==entryQuery) {
                entryQuery.setValue(currentDocumentHistogram);
            }
        }
        trainingSet.remove(entryQuery);
        /**
         * I must decrement the variable containing the total number of documents in the training set,
         * because I just deleted from it the query, which was temporarly added at the beginning of the method.
         */
        totalNumberDocuments--;
        /**
         * Calculation of the cosine of each training set's document to the query.
         */
        double twoNormQuery = twoNorm(entryQuery.getValue());
        for (int i = 0; i < totalNumberDocuments; i++) {
            currentDocumentHistogram = copyTrainingSet.get(i).getValue();
            double distance=scalarProduct(entryQuery.getValue(), currentDocumentHistogram) / (twoNorm(currentDocumentHistogram) * twoNormQuery);
            trainingSet.get(i).getKey().put("Distance", distance);
            trainingSet.get(i).getKey().put("Angle",Math.toDegrees(Math.acos(distance)));
        }
    }
    
    /**
     * Method to calculate the scalar product between 2 vectors (of doubles), represented in this case by
     * two maps {@link String}->{@link Double}.
     * 
     * @param A             The 1st vector.
     * @param B             The 2nd vector.
     * @return              The result of the scalar product between A and B.
     */
    private static double scalarProduct(Map<String, Double> A, Map<String, Double> B) {
        
        double product = 0.0;
        Set<String> intersection = new HashSet<String>();
        intersection.addAll(A.keySet());
        /**
         * A and B are intersected, and a new set is created from the result of the operation.
         * This will be used to perform the actual calculation, because it would be a waste of time taking
         * into consideration also terms not common between the two vectors, since they give a contribution
         * of 0 to the final result.
         */
        if (A != B) {
            intersection.retainAll(B.keySet());
        }
        for (String token : intersection) {
            product += (A.get(token) * B.get(token));
        }
        return product;
    }
    
    /**
     * Method to calculate the 2-norm of a vector in input, represented by a map
     * {@link String}->{@link Double}.
     * 
     * @param V             Input vector.
     * @return              2-norm of the input vector.
     */
    private static double twoNorm(Map<String, Double> V) {
        return Math.sqrt(scalarProduct(V, V));
    }
}
