/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *******************************************************************************
 * Copyright (C) 1996-2009, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package sun.text.normalizer;

/**
 * <code>ReplaceableString</code> is an adapter class that implements the
 * <code>Replaceable</code> API around an ordinary <code>StringBuffer</code>.
 *
 * <p><em>Note:</em> This class does not support attributes and is not
 * intended for general use.  Most clients will need to implement
 * {@link Replaceable} in their text representation class.
 *
 * <p>Copyright &copy; IBM Corporation 1999.  All rights reserved.
 *
 * @see Replaceable
 * @author Alan Liu
 * @stable ICU 2.0
 */
class ReplaceableString implements Replaceable {

    private StringBuffer buf;

    /**
     * Construct a new object with the given initial contents.
     * @param str initial contents
     * @stable ICU 2.0
     */
    public ReplaceableString(String str) {
        buf = new StringBuffer(str);
    }

    /**
     * Construct a new object using <code>buf</code> for internal
     * storage.  The contents of <code>buf</code> at the time of
     * construction are used as the initial contents.  <em>Note!
     * Modifications to <code>buf</code> will modify this object, and
     * vice versa.</em>
     * @param buf object to be used as internal storage
     * @stable ICU 2.0
     */
    public ReplaceableString(StringBuffer buf) {
        this.buf = buf;
    }

    /**
     * Return the number of characters contained in this object.
     * <code>Replaceable</code> API.
     * @stable ICU 2.0
     */
    public int length() {
        return buf.length();
    }

    /**
     * Return the character at the given position in this object.
     * <code>Replaceable</code> API.
     * @param offset offset into the contents, from 0 to
     * <code>length()</code> - 1
     * @stable ICU 2.0
     */
    public char charAt(int offset) {
        return buf.charAt(offset);
    }

    /**
     * Copies characters from this object into the destination
     * character array.  The first character to be copied is at index
     * <code>srcStart</code>; the last character to be copied is at
     * index <code>srcLimit-1</code> (thus the total number of
     * characters to be copied is <code>srcLimit-srcStart</code>). The
     * characters are copied into the subarray of <code>dst</code>
     * starting at index <code>dstStart</code> and ending at index
     * <code>dstStart + (srcLimit-srcStart) - 1</code>.
     *
     * @param srcStart the beginning index to copy, inclusive;
     *        {@code 0 <= start <= limit}.
     * @param srcLimit the ending index to copy, exclusive;
     *        {@code start <= limit <= length()}.
     * @param dst the destination array.
     * @param dstStart the start offset in the destination array.
     * @stable ICU 2.0
     */
    public void getChars(int srcStart, int srcLimit, char dst[], int dstStart) {
        if (srcStart != srcLimit) {
            buf.getChars(srcStart, srcLimit, dst, dstStart);
        }
    }
}
