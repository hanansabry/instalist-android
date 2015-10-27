package org.noorganization.instalist.model;

import com.orm.StringUtil;
import com.orm.SugarRecord;
import com.orm.query.Condition;
import com.orm.query.Select;

import java.util.List;

/**
 * Represents a logical recipe. Like ShoppingList, it does not contain a real java list.
 * Created by michi on 14.04.15.
 */
public class Recipe extends SugarRecord<Recipe> {

    /**
     * @deprecated use instead {@link Recipe#COLUMN_NAME}
     */
    public final static String ATTR_NAME = StringUtil.toSQLName("mName");

    public final static String TABLE_NAME = "recipe";

    public final static String COLUMN_ID = "_id";

    public final static String COLUMN_NAME = "name";

    public final static String DATABASE_CREATE = "CREATE TABLE " + TABLE_NAME
            + "("
            + COLUMN_ID + " TEXT PRIMARY KEY,"
            + COLUMN_NAME + " TEXT"
            + ")";

    public String mName;

    public Recipe() {
        mName = "";
    }

    public Recipe(String _name) {
        mName = _name;
    }

    public List<Ingredient> getIngredients() {
        return Select.from(Ingredient.class).where(Condition.prop("m_recipe").eq(getId())).list();
    }

    @Override
    public boolean equals(Object anotherObject) {
        if (this == anotherObject) {
            return true;
        }
        if (anotherObject == null || getClass() != anotherObject.getClass()) {
            return false;
        }

        Recipe anotherRecipe = (Recipe) anotherObject;

        return mName.equals(anotherRecipe.mName) && getId().compareTo(anotherRecipe.getId()) == 0;
    }

    @Override
    public int hashCode() {
        return getId().intValue();
    }
}
