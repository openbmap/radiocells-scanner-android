/**
 * 
 */
package org.openbmap.db.model;

/**
 * @author power
 *
 */
public abstract class AbstractLogEntry<T> implements Comparable<T> {

	@Override
	public abstract int compareTo(final T compareTo);


}
