/*
 * This file is generated by jOOQ.
*/
package org.ensembl.database.homo_sapiens_core.tables;


import java.util.Arrays;
import java.util.List;

import javax.annotation.Generated;

import org.ensembl.database.homo_sapiens_core.HomoSapiensCore_89_37;
import org.ensembl.database.homo_sapiens_core.Keys;
import org.ensembl.database.homo_sapiens_core.tables.records.MiscFeatureMiscSetRecord;
import org.jooq.Field;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.TableImpl;
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
public class MiscFeatureMiscSet extends TableImpl<MiscFeatureMiscSetRecord> {

    private static final long serialVersionUID = -1254255580;

    /**
     * The reference instance of <code>homo_sapiens_core_89_37.misc_feature_misc_set</code>
     */
    public static final MiscFeatureMiscSet MISC_FEATURE_MISC_SET = new MiscFeatureMiscSet();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<MiscFeatureMiscSetRecord> getRecordType() {
        return MiscFeatureMiscSetRecord.class;
    }

    /**
     * The column <code>homo_sapiens_core_89_37.misc_feature_misc_set.misc_feature_id</code>.
     */
    public final TableField<MiscFeatureMiscSetRecord, UInteger> MISC_FEATURE_ID = createField("misc_feature_id", org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false).defaultValue(org.jooq.impl.DSL.inline("0", org.jooq.impl.SQLDataType.INTEGERUNSIGNED)), this, "");

    /**
     * The column <code>homo_sapiens_core_89_37.misc_feature_misc_set.misc_set_id</code>.
     */
    public final TableField<MiscFeatureMiscSetRecord, UShort> MISC_SET_ID = createField("misc_set_id", org.jooq.impl.SQLDataType.SMALLINTUNSIGNED.nullable(false).defaultValue(org.jooq.impl.DSL.inline("0", org.jooq.impl.SQLDataType.SMALLINTUNSIGNED)), this, "");

    /**
     * Create a <code>homo_sapiens_core_89_37.misc_feature_misc_set</code> table reference
     */
    public MiscFeatureMiscSet() {
        this("misc_feature_misc_set", null);
    }

    /**
     * Create an aliased <code>homo_sapiens_core_89_37.misc_feature_misc_set</code> table reference
     */
    public MiscFeatureMiscSet(String alias) {
        this(alias, MISC_FEATURE_MISC_SET);
    }

    private MiscFeatureMiscSet(String alias, Table<MiscFeatureMiscSetRecord> aliased) {
        this(alias, aliased, null);
    }

    private MiscFeatureMiscSet(String alias, Table<MiscFeatureMiscSetRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() {
        return HomoSapiensCore_89_37.HOMO_SAPIENS_CORE_89_37;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UniqueKey<MiscFeatureMiscSetRecord> getPrimaryKey() {
        return Keys.KEY_MISC_FEATURE_MISC_SET_PRIMARY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UniqueKey<MiscFeatureMiscSetRecord>> getKeys() {
        return Arrays.<UniqueKey<MiscFeatureMiscSetRecord>>asList(Keys.KEY_MISC_FEATURE_MISC_SET_PRIMARY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MiscFeatureMiscSet as(String alias) {
        return new MiscFeatureMiscSet(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public MiscFeatureMiscSet rename(String name) {
        return new MiscFeatureMiscSet(name, null);
    }
}
