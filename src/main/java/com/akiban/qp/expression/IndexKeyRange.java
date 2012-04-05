/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.qp.expression;

import com.akiban.qp.rowtype.IndexRowType;
import com.akiban.server.api.dml.ColumnSelector;

public class IndexKeyRange
{
    public String toString()
    {
        StringBuilder buffer = new StringBuilder();
        buffer.append('(');
        if (lo != null) {
            buffer.append(loInclusive() ? ">=" : ">");
            buffer.append(lo.toString());
        }
        buffer.append(',');
        if (hi != null) {
            buffer.append(hiInclusive() ? "<=" : "<");
            buffer.append(hi.toString());
        }
        buffer.append(')');
        return buffer.toString();
    }

    public IndexRowType indexRowType()
    {
        return indexRowType;
    }

    public IndexBound lo()
    {
        return lo;
    }

    public IndexBound hi()
    {
        return hi;
    }

    public boolean loInclusive()
    {
        return loInclusive;
    }

    public boolean hiInclusive()
    {
        return hiInclusive;
    }

    public boolean unbounded()
    {
        return lo == null && hi == null;
    }

    public int boundColumns()
    {
        return boundColumns;
    }

    /**
     * Describes a full index scan.
     * @param indexRowType The row type of index keys.
     * @return IndexKeyRange covering all keys of the index.
     */
    public static IndexKeyRange unbounded(IndexRowType indexRowType)
    {
        return new IndexKeyRange(indexRowType);
    }

    /**
     * Describes a range of keys between lo and hi. The bounds are inclusive or not depending on
     * loInclusive and hiInclusive. lo and hi must both be non-null. There are constraints on the bounds:
     * - The ColumnSelectors for lo and hi must select for the same columns.
     * - The selected columns must be leading columns of the index.
     *
     * @param indexRowType The row type of index keys.
     * @param lo           Lower bound of the range.
     * @param loInclusive  True if the lower bound is inclusive, false if exclusive.
     * @param hi           Upper bound of the range.
     * @param hiInclusive  True if the upper bound is inclusive, false if exclusive.
     * @return IndexKeyRange covering the keys lying between lo and hi, subject to the loInclusive and
     * hiInclusive flags.
     */
    public static IndexKeyRange bounded(IndexRowType indexRowType,
                                        IndexBound lo,
                                        boolean loInclusive,
                                        IndexBound hi,
                                        boolean hiInclusive)
    {
        if (lo == null || hi == null) {
            throw new IllegalArgumentException("IndexBound arguments must not be null");
        }
        return new IndexKeyRange(indexRowType, lo, loInclusive, hi, hiInclusive);
    }

    /**
     * Describes all keys in the index starting at or after lo, depending on loInclusive.
     *
     * @param indexRowType The row type of index keys.
     * @param lo           Lower bound of the range.
     * @param loInclusive  True if the lower bound is inclusive, false if exclusive.
     * @return IndexKeyRange covering the keys starting at or after lo.
     */
    public static IndexKeyRange startingAt(IndexRowType indexRowType,
                                           IndexBound lo,
                                           boolean loInclusive)
    {
        if (lo == null) {
            throw new IllegalArgumentException("IndexBound argument must not be null");
        }
        return new IndexKeyRange(indexRowType, lo, loInclusive, null, false);
    }

    /**
     * Describes all keys in the index starting at or after lo, depending on loInclusive.
     *
     * @param indexRowType The row type of index keys.
     * @param hi           Upper bound of the range.
     * @param hiInclusive  True if the upper bound is inclusive, false if exclusive.
     * @return IndexKeyRange covering the keys ending at or before lo.
     */
    public static IndexKeyRange endingAt(IndexRowType indexRowType,
                                         IndexBound hi,
                                         boolean hiInclusive)
    {
        if (hi == null) {
            throw new IllegalArgumentException("IndexBound argument must not be null");
        }
        return new IndexKeyRange(indexRowType, null, false, hi, hiInclusive);
    }

    // An Akiban index scan normally allows a range for only the last specified part of the bound. E.g.,
    // (1, 10, 800) - (1, 10, 888) is legal, but (1, 10, 800) - (1, 20, 888) is not, because there are two ranges,
    // 10-20 and 800-888. MySQL support requires a different approach in which we start at the lower bound and
    // scan everything in the index up to the upper bound. So (1, 10, 800) - (1, 20, 888) is legal, and could return
    // a row that is lexicographically between these bounds, but outside some range, e.g. (1, 11, 900). This will
    // also be useful in supporting queries such as select * from t where (x, y) > (5, 7).
    public void lexicographic(boolean lexicographic)
    {
        this.lexicographic = lexicographic;
    }

    public boolean lexicographic()
    {
        // return (lo == null || hi == null) && lo != hi;
        return lexicographic;
    }

    private IndexKeyRange(IndexRowType indexRowType)
    {
        this.boundColumns = 0;
        this.indexRowType = indexRowType;
        this.lo = null;
        this.loInclusive = false;
        this.hi = null;
        this.hiInclusive = false;
    }

    private IndexKeyRange(IndexRowType indexRowType,
                          IndexBound lo,
                          boolean loInclusive,
                          IndexBound hi,
                          boolean hiInclusive)
    {
        this.boundColumns =
            lo == null
            ? boundColumns(indexRowType, hi) :
            hi == null
            ? boundColumns(indexRowType, lo)
            : boundColumns(indexRowType, lo, hi);
        this.indexRowType = indexRowType;
        this.lo = lo;
        this.loInclusive = loInclusive;
        this.hi = hi;
        this.hiInclusive = hiInclusive;
    }

    private static int boundColumns(IndexRowType indexRowType, IndexBound lo, IndexBound hi)
    {
        ColumnSelector loSelector = lo.columnSelector();
        ColumnSelector hiSelector = hi.columnSelector();
        boolean selected = true;
        int boundColumns = 0;
        for (int i = 0; i < indexRowType.declaredFields(); i++) {
            if (loSelector.includesColumn(i) != hiSelector.includesColumn(i)) {
                throw new IllegalArgumentException(
                    String.format("IndexBound arguments specify different fields of index %s", indexRowType));
            }
            if (selected) {
                if (loSelector.includesColumn(i)) {
                    boundColumns++;
                } else {
                    selected = false;
                }
            } else {
                if (loSelector.includesColumn(i)) {
                    throw new IllegalArgumentException(
                        String.format("IndexBound arguments for index %s specify non-leading fields", indexRowType));
                }
            }
        }
        assert boundColumns > 0;
        return boundColumns;
    }

    private static int boundColumns(IndexRowType indexRowType, IndexBound bound)
    {
        ColumnSelector selector = bound.columnSelector();
        boolean selected = true;
        int boundColumns = 0;
        for (int i = 0; i < indexRowType.declaredFields(); i++) {
            if (selected) {
                if (selector.includesColumn(i)) {
                    boundColumns++;
                } else {
                    selected = false;
                }
            } else {
                if (selector.includesColumn(i)) {
                    throw new IllegalArgumentException(
                        String.format("IndexBound arguments for index %s specify non-leading fields", indexRowType));
                }
            }
        }
        assert boundColumns > 0;
        return boundColumns;
    }

    // Object state

    private final IndexRowType indexRowType;
    private final int boundColumns;
    private final IndexBound lo;
    private final boolean loInclusive;
    private final IndexBound hi;
    private final boolean hiInclusive;
    private boolean lexicographic = false;
}
