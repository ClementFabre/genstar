package core.util.random;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class GenstarRandomUtils {

	/**
	 * Returns one element uniformly picked from the list
	 * @param l
	 * @return
	 */
	public static <T> T oneOf(List<T> l) {
		
		// check param
		if (l.isEmpty())
			throw new IllegalArgumentException("cannot take one value out of an empty list");
		
		// quick exit 
		if (l.size() == 1)
			return l.get(0);
		
		return l.get(GenstarRandom.getInstance().nextInt(l.size()));
	}
	
	/**
	 * returns one element for a set. 
	 * Warning: should in theory not be used on a Set which is not Sorted or ordered, 
	 * as this underlying random order breaks the reproductibility of the generation. 
	 * @param s
	 * @return
	 */
	public static <T> T oneOf(Set<T> s) {
		
		// check param
		if (s.isEmpty())
			throw new IllegalArgumentException("cannot take one value out of an empty set");
		
		// quick exit 
		if (s.size() == 1)
			return s.iterator().next();
		
		final int idx = GenstarRandom.getInstance().nextInt(s.size());
		Iterator<T> it = s.iterator();
		for (int i=0; i<idx; i++) {
			it.next();
		}
		return it.next();
	}
	
	private GenstarRandomUtils() {}

}
