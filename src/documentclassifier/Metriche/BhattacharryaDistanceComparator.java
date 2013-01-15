
package documentclassifier.Metriche;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * This class implements the Bhattacharrya distance, using as the weighting function for the terms of each document
 * the relative frequency, already calculated during the generation of each document's histogram.
 * <p>
 * It implements the interface {@link Comparator}, on elements of type Entry<Map<String,Object>, Map<String, Double>>,
 * each one containing information on single documents of the training set.
 * Using the information present in each entry, the Comparator orders the training set's documents on their Bhattacharrya distance
 * to the query document.
 * 
 * @author      Salvo Danilo Giuffrida (salvod.giuffrida@gmail.com)
 * @see         Comparator
 */
public class BhattacharryaDistanceComparator implements Comparator<Entry<Map<String,Object>,Map<String,Double>>> {
    
    /**
     * Method that implements the interface {@link Comparator}, to compare two documents whose Bhattacharrya
     * distance to the query document has been calculated.
     * <p>
     * The comparation then is done using this metric, whose value has been calculated previously, using the static method
     * {@link #calculateDistance calculateDistance}, and returning in accordance the value 1, 0 or -1.
     * <p>
     * This method gets called for each possible pair of documents in the training set.
     * 
     * @param A             The 1st document to compare.
     * @param B             The 2nd document to compare.
     * @return              An integer between these 3 possible values: -1, 0 or 1, representing the ordering
     *                      between the 1st and the 2nd input document.
     */
    public int compare(Entry<Map<String,Object>,Map<String,Double>> A, Entry<Map<String,Object>,Map<String,Double>> B) {

        /**
         * This is the distance of A from the current query document, calculated previously
         * with the method {@link #calculateDistance calculateDistance}.
         */
        double distanceA=(Double) A.getKey().get("Distance");
        /**
         * This is the distance of B from the current query document.
         */
        double distanceB=(Double) B.getKey().get("Distance");
        if( distanceA > distanceB ) {
            return 1;
        }
        else if ( distanceA == distanceB ) {
            return 0;
        }
        else {
            return -1;
        }
    }
    
    /**
     * Static method used for calculation of the Bhattacharrya distance, between each document of a set (represented by
     * a vector that for each document's term contains its relative frequency), and a reference document (query).
     * 
     * @param documentSet   A set of documents, represented with a list of instances of the class Entry<Map<String,Object>, Map<String, Double>>,
     *                      whose key is a map {@link String}->{@link Object}, containing information on a document, and whose value is a map
     *                      {@link String}->{@link Double}, which represents the histogram of terms of the document (after pre-processing).
     * @param query         The histogram of the query document all the cosines refer to.
     *                      It is implemented by a map, that associates keys of type {@link String} to elements of type {@link Double}.
     */
    public static void calculateDistance(LinkedList<Entry<Map<String, Object>, Map<String, Double>>> documentSet, Map<String, Double> query) {
        
        Map<String,Double> currentHistogram;
        Set<String> intersection;
        /**
         * For each document of the document set we calculate the scalar product between it and the query document,
         * but taking into consideration the square root of their histogram's components.
         * At the end of the calculation, we apply the formula of the Bhattacharrya distance: d(A,B)=sqrt(1-prodottoscalare).
         */
        for(Entry<Map<String,Object>,Map<String,Double>> currentDocument : documentSet ) {
            
            double sum=0.0;
            currentHistogram=currentDocument.getValue();
            /**
             * The current document and the query document are intersected, and the calculation of the Bhattacharrya
             * distance takes into consideration only the common terms between them.
             * It would in fact be a waste of computational time to try to calculate the scalar product, taking into
             * consideration also terms not common between them, because in any case they give a contribution of 0
             * to the final result.
             */
            intersection=new HashSet<String>();
            intersection.addAll(currentHistogram.keySet());
            intersection.retainAll(query.keySet());
            for ( String Token : intersection ) {
                sum = sum + (Math.sqrt(query.get(Token) * currentHistogram.get(Token)));
            }
            currentDocument.getKey().put("Distance", new Double(Math.sqrt(1-sum)));
        }
    }
    
}
