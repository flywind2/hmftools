/*
 * This file is generated by jOOQ.
*/
package org.ensembl.database.homo_sapiens_core.tables.records;


import javax.annotation.Generated;

import org.ensembl.database.homo_sapiens_core.tables.MiscFeatureMiscSet;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Row2;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.UInteger;
import org.jooq.types.UShort;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.9.5"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class MiscFeatureMiscSetRecord extends UpdatableRecordImpl<MiscFeatureMiscSetRecord> implements Record2<UInteger, UShort> {

    private static final long serialVersionUID = -1725067518;

    /**
     * Setter for <code>homo_sapiens_core_89_37.misc_feature_misc_set.misc_feature_id</code>.
     */
    public void setMiscFeatureId(UInteger value) {
        set(0, value);
    }

    /**
     * Getter for <code>homo_sapiens_core_89_37.misc_feature_misc_set.misc_feature_id</code>.
     */
    public UInteger getMiscFeatureId() {
        return (UInteger) get(0);
    }

    /**
     * Setter for <code>homo_sapiens_core_89_37.misc_feature_misc_set.misc_set_id</code>.
     */
    public void setMiscSetId(UShort value) {
        set(1, value);
    }

    /**
     * Getter for <code>homo_sapiens_core_89_37.misc_feature_misc_set.misc_set_id</code>.
     */
    public UShort getMiscSetId() {
        return (UShort) get(1);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Record2<UInteger, UShort> key() {
        return (Record2) super.key();
    }

    // -------------------------------------------------------------------------
    // Record2 type implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Row2<UInteger, UShort> fieldsRow() {
        return (Row2) super.fieldsRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Row2<UInteger, UShort> valuesRow() {
        return (Row2) super.valuesRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<UInteger> field1() {
        return MiscFeatureMiscSet.MISC_FEATURE_MISC_SET.MISC_FEATURE_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<UShort> field2() {
        return MiscFeatureMiscSet.MISC_FEATURE_MISC_SET.MISC_SET_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UInteger value1() {
        return getMiscFeatureId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UShort value2() {
        return getMiscSetId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MiscFeatureMiscSetRecord value1(UInteger value) {
        setMiscFeatureId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MiscFeatureMiscSetRecord value2(UShort value) {
        setMiscSetId(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MiscFeatureMiscSetRecord values(UInteger value1, UShort value2) {
        value1(value1);
        value2(value2);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached MiscFeatureMiscSetRecord
     */
    public MiscFeatureMiscSetRecord() {
        super(MiscFeatureMiscSet.MISC_FEATURE_MISC_SET);
    }

    /**
     * Create a detached, initialised MiscFeatureMiscSetRecord
     */
    public MiscFeatureMiscSetRecord(UInteger miscFeatureId, UShort miscSetId) {
        super(MiscFeatureMiscSet.MISC_FEATURE_MISC_SET);

        set(0, miscFeatureId);
        set(1, miscSetId);
    }
}
