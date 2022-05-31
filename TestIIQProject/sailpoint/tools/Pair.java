/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

public class Pair<T1,T2>
{
    private T1 _first;
    private T2 _second;

    /**
     * Convenience method for making a pair.
     * @param first The first entry.
     * @param second The second entry.
     * @return The new pair instance.
     */
    public static <T1, T2> Pair<T1, T2> make(T1 first, T2 second) {
        return new Pair<T1, T2>(first, second);
    }

    public Pair(T1 first, T2 second)
    {
        _first  = first;
        _second = second;
    }

    public T1 getFirst()
    {
        return _first;
    }

    public T2 getSecond()
    {
        return _second;
    }

    public void setFirst(T1 first)
    {
        _first = first;
    }

    public void setSecond(T2 second)
    {
        _second = second;
    }

    public int hashCode()
    {
        int rv = 0;
        if (_first != null)
        {
            rv ^= _first.hashCode();
        }
        if (_second != null)
        {
            rv ^= _second.hashCode();
        }
        return rv;
    }

    private static boolean equals(Object o1, Object o2)
    {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    public boolean equals(Object o)
    {
        if (o instanceof Pair)
        {
            Pair other = (Pair)o;
            return ( equals(_first,other._first) &&
                     equals(_second,other._second) );
        }
        return false;
    }

    public String toString()
    {
        return "( "+_first+", "+_second+" )";
    }
}
