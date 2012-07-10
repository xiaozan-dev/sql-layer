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
package com.akiban.server.collation;

import com.akiban.server.types.ValueSource;
import com.persistit.Key;

public abstract class AkCollator {

    private final String collatorName;

    private final String collatorScheme;

    private final int collationId;

    protected AkCollator(final String collatorName, final String collatorScheme, final int collationId) {
        this.collatorName = collatorName;
        this.collatorScheme = collatorScheme;
        this.collationId = collationId;
    }

    /**
     * @return true if this collator is capable of recovering the key string
     *         from a key segment.
     */
    abstract public boolean isRecoverable();

    /**
     * Append a String to a Key
     * 
     * @param key
     * @param value
     */
    abstract public void append(Key key, String value);

    /**
     * Decode a String from a Key segment
     * 
     * @param key
     * @return the decoded String
     */
    abstract public String decode(Key key);

    /**
     * Construct the sort key bytes for the given String value
     * 
     * @param value
     *            the String
     * @return sort key bytes, last byte only must be zero
     */
    abstract public byte[] encodeSortKeyBytes(String value);

    /**
     * Recover the value or throw an unsupported exception.
     * 
     * @param bytes
     *            the sort key bytes
     * @param index
     *            index within array of first sorted key byte
     * @param length
     *            number of sorted key bytes
     * @return the decoded String value
     * @throws UnsupportedOperationException
     *             if unable to decode sort keys
     */
    abstract public String decodeSortKeyBytes(byte[] bytes, int index, int length);

    /**
     * Compare two string values: Comparable<ValueSource>
     */
    abstract public int compare(ValueSource value1, ValueSource value2);

    /**
     * Compare two string objects: Comparable<String>
     */
    abstract public int compare(String string1, String string2);

    /**
     * @return whether the underlying collation scheme is case-sensitive
     */
    abstract public boolean isCaseSensitive();

    @Override
    public String toString() {
        return collatorName + "(" + collatorScheme + ")";
    }

    public int getCollationId() {
        return collationId;
    }

    public String getName() {
        return collatorName;
    }

    public String getScheme() {
        return collatorScheme;
    }

}
