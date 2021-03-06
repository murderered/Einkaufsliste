/*
 * Copyright 2015 Michael Wodniok
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  This file is part of Einkaufsliste.
 */

package org.noorganization.shoppinglist.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;
import android.util.SparseArray;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class ModelManager {

    public static final int INVALID_ID = 0xFFFFFFFF;

    List<Product>      m_allProducts;
    List<ShoppingList> m_allLists;
    List<Unit>         m_allUnits;
    boolean            m_loaded;

    static ModelManager m_sInstance;

    private ModelManager() {
        m_allProducts = new LinkedList<>();
        m_allLists    = new LinkedList<>();
        m_allUnits    = new LinkedList<>();
        m_loaded      = false;
    }

    /**
     * Creates a new Product and registers it. This method is does not throw anything if saving to database fails (see
     * return value).
     * @param _title A name for the Product. null is not allowed
     * @param _defaultValue The default value when adding to a shopping list.
     * @param _unitId The id of the referencing Unit returned by {@link Unit#Id}. {@link #INVALID_ID} is also allowed
     *                and means something like "this product should have no unit".
     * @param _db Open connection to a writable database.
     * @return The created Product. Null if saving to database failed.
     */
    public Product createProduct(String _title, float _defaultValue, int _unitId, SQLiteDatabase _db) {
        if (_db == null || _title == null) {
            throw new IllegalArgumentException("Title or database parameter was null.");
        }

        Product newProduct = new Product();
        newProduct.Title        = _title;
        newProduct.DefaultValue = _defaultValue;
        newProduct.UnitId       = _unitId;
        newProduct.Id           = generateId(m_allProducts.toArray(new Product[m_allProducts.size()]));

        ContentValues insertionValues = new ContentValues();
        insertionValues.put("title", _title);
        insertionValues.put("defaultvalue", _defaultValue);
        insertionValues.put("unit_id", (_unitId == INVALID_ID ? null : _unitId));
        insertionValues.put("id", newProduct.Id);
        if(_db.insert("Products", null, insertionValues) == -1) {
            return null;
        }

        m_allProducts.add(newProduct);

        return new Product(newProduct);
    }

    /**
     * Creates a ShoppingList and registers it automatically to the List of ShoppingList's. No exception is thrown when
     * saving fails. Check the returned object.
     * @param _title Title for the new List, simply not null.
     * @param _db Open connection to a writable database.
     * @return The constructed and registered ShoppingList, Or null if saving to database failed.
     */
    public ShoppingList createShoppingList(String _title, SQLiteDatabase _db) {
        if (_db == null || _title == null) {
            throw new IllegalArgumentException("A parameter was not valid, because null is not allowed.");
        }

        ShoppingList newList = new ShoppingList();
        newList.Title       = _title;
        newList.Id          = generateId(m_allLists.toArray(new ShoppingList[m_allLists.size()]));
        newList.ListEntries = new SparseArray<>();


        ContentValues insertionValues = new ContentValues();
        insertionValues.put("title", _title);
        insertionValues.put("id", newList.Id);
        if(_db.insert("ShoppingLists", null, insertionValues) == -1) {
            return null;
        }

        m_allLists.add(newList);

        return new ShoppingList(newList);
    }

    /**
     * Creates a Unit and registers it automatically in the list of all Units. No exception will be thrown if saving
     * fails.
     * @param _unitText The "name" of the unit e.g. "kg" (kilogram) or "l" (liter). null is not valid.
     * @param _db Open connection to a writable database.
     * @return The created and saved object or null if saving did not work.
     */
    public Unit createUnit(String _unitText, SQLiteDatabase _db) {
        if (_db == null || _unitText == null) {
            throw new IllegalArgumentException("A parameter was null. This is not allowed.");
        }

        Unit newUnit = new Unit();
        newUnit.UnitText = _unitText;
        newUnit.Id       = generateId(m_allUnits.toArray(new IdentificableModelObject[m_allUnits.size()]));


        ContentValues insertionValues = new ContentValues();
        insertionValues.put("title", _unitText);
        insertionValues.put("id", newUnit.Id);
        if(_db.insert("Units", null, insertionValues) == -1) {
            return null;
        }

        m_allUnits.add(newUnit);

        return new Unit(newUnit);
    }

    /**
     * Generates a unique id for an IdentificableModelObject object.
     * @return A collision-free, random id
     */
    static int generateId(IdentificableModelObject _existingObjects[]) {
        Random randomGenerator = new Random();
        randomGenerator.setSeed(SystemClock.uptimeMillis());
        while (true) {
            int newId = randomGenerator.nextInt();
            if (newId == INVALID_ID) {
                continue;
            }
            if (!idExists(newId, _existingObjects)) {
                return newId;
            }
        }
    }

    static boolean idExists(int _idToCheck, IdentificableModelObject _existingObjects[]) {
        for (IdentificableModelObject currentObject : _existingObjects) {
            if (currentObject.Id == _idToCheck) {
                return true;
            }
        }
        return false;
    }

    /**
     * Opens or creates a database in this context with the specified name.
     * @param _context Null is not allowed.
     * @param _name Null is not allowed.
     * @return The open database. It's writable and can be used for all model-modifying methods.
     */
    public SQLiteDatabase openAndReadDatabase(Context _context, String _name) {
        if (_context == null || _name == null) {
            throw new IllegalArgumentException("A parameter was null.");
        }

        String realFilename = _name;
        if (!realFilename.endsWith(".db")) {
            realFilename = realFilename.concat(".db");
        }

        DBOpenHelper databaseHelper = new DBOpenHelper(_context, realFilename, null, DBOpenHelper.CURRENT_DATABASE_VERSION);
        SQLiteDatabase db = databaseHelper.getWritableDatabase();

        m_allUnits.clear();

        Cursor allUnits = db.query("Units",
                new String[]{ "id", "title" },
                null, new String[0], null, null, null);
        allUnits.moveToFirst();
        while (!allUnits.isAfterLast()) {
            Unit existingUnit = new Unit();
            existingUnit.UnitText = allUnits.getString(allUnits.getColumnIndex("title"));
            existingUnit.Id       = allUnits.getInt(allUnits.getColumnIndex("id"));
            m_allUnits.add(existingUnit);
            allUnits.moveToNext();
        }
        allUnits.close();

        m_allProducts.clear();

        Cursor allProducts = db.query("Products",
                new String[]{ "id", "title", "defaultvalue", "unit_id" },
                null, new String[0], null, null, null);
        allProducts.moveToFirst();
        int indexOfUnitId = allProducts.getColumnIndex("unit_id");
        while (!allProducts.isAfterLast()) {
            Product existingProduct = new Product();
            existingProduct.DefaultValue = allProducts.getFloat(allProducts.getColumnIndex("defaultvalue"));
            existingProduct.Title        = allProducts.getString(allProducts.getColumnIndex("title"));
            existingProduct.Id           = allProducts.getInt(allProducts.getColumnIndex("id"));
            existingProduct.UnitId       = (allProducts.isNull(indexOfUnitId) ?
                    INVALID_ID : allProducts.getInt(indexOfUnitId));
            m_allProducts.add(existingProduct);
            allProducts.moveToNext();
        }
        allProducts.close();

        m_allLists.clear();

        Cursor allLists = db.query("ShoppingLists",
                new String[]{ "id", "title" },
                null, new String[0], null, null, null);
        allLists.moveToFirst();
        while (!allLists.isAfterLast()) {
            ShoppingList existingList = new ShoppingList();
            existingList.Title       = allLists.getString(allLists.getColumnIndex("title"));
            existingList.Id          = allLists.getInt(allLists.getColumnIndex("id"));
            existingList.ListEntries = new SparseArray<>();
            Cursor allItemsInList = db.query("ProductsInShoppingLists",
                    new String[] { "product_id", "value" },
                    "shoppinglist_id = ?",
                    new String[] { existingList.Id+"" },
                    null, null,
                    "product_id ASC"); // product-id's and -values where shoppinglist_id is the current id, ordered.
            for (allItemsInList.moveToFirst(); !allItemsInList.isAfterLast(); allItemsInList.moveToNext()) {
                existingList.ListEntries.append(allItemsInList.getInt(allItemsInList.getColumnIndex("product_id")),
                        allItemsInList.getFloat(allItemsInList.getColumnIndex("value")));
            }
            allItemsInList.close();

            m_allLists.add(existingList);
            allLists.moveToNext();
        }
        allLists.close();

        m_loaded = true;

        return db;
    }

    /**
     * @return A list with copies of all Products. The array is not sorted. null wont be returned even if no Products
     * are loaded.
     */
    public Product[] getAllProducts() {
        if (m_allProducts.size() == 0) {
            return new Product[0];
        }

        Product rtn[] = new Product[m_allProducts.size()];
        int index = 0;
        for (Product currentProduct : m_allProducts) {
            rtn[index] = new Product(currentProduct);
            index++;
        }
        return rtn;
    }

    /**
     * @return A list with copies of all Units. The array is not sorted. null wont be returned even if no Units are
     * loaded.
     */
    public Unit[] getAllUnits() {
        if (m_allUnits.size() == 0) {
            return new Unit[0];
        }

        Unit rtn[] = new Unit[m_allUnits.size()];
        int index = 0;
        for (Unit currentUnit : m_allUnits) {
            rtn[index] = new Unit(currentUnit);
            index++;
        }
        return rtn;
    }

    /**
     * @return A list of all ShoppingLists (as copies). Never null. Not sorted.
     */
    public ShoppingList[] getAllShoppingLists() {
        if (m_allLists.size() == 0) {
            return new ShoppingList[0];
        }

        ShoppingList rtn[] = new ShoppingList[m_allLists.size()];
        int index = 0;
        for (ShoppingList currentList : m_allLists) {
            rtn[index] = new ShoppingList(currentList);
            index++;
        }
        return rtn;
    }

    /**
     * @param _id Internal id of Product. If {@link #INVALID_ID}, none will be found.
     * @return A copy of the Product or null, if nothing found.
     */
    public Product getProductById(int _id) {
        if (_id == INVALID_ID) {
            return null;
        }
        for (Product currentProduct : m_allProducts) {
            if (currentProduct.Id == _id) {
                return new Product(currentProduct);
            }
        }
        return null;
    }

    /**
     * Searches a Unit by it's id.
     * @param _id The internal id of the unit. If {@link #INVALID_ID} is provided, no object will be found.
     * @return A Unit if found or null if no Unit was found.
     */
    public Unit getUnitById(int _id) {
        if (_id == INVALID_ID) {
            return null;
        }
        for (Unit CurrentUnit : m_allUnits) {
            if (CurrentUnit.Id == _id) {
                return new Unit(CurrentUnit);
            }
        }
        return null;
    }

    /**
     * @param _id Internal id of the list. If {@link #INVALID_ID}, none will be found.
     * @return A copy of the ShoppingList or null, if nothing found.
     */
    public ShoppingList getShoppingListById(int _id) {
        if (_id == INVALID_ID) {
            return null;
        }
        for (ShoppingList currentList : m_allLists) {
            if (currentList.Id == _id) {
                return new ShoppingList(currentList);
            }
        }
        return null;
    }

    /**
     * @param _unitToUpdate The changed unit. Changing the id is dangerous, since another unit may be overwritten.
     * @param _db Open connection to a writable database.
     * @return Whether update succeeded.
     */
    public boolean updateUnit(Unit _unitToUpdate, SQLiteDatabase _db) {
        if (_db == null || _unitToUpdate == null || _unitToUpdate.UnitText == null) {
            throw new IllegalArgumentException("A parameter was not valid.");
        }

        if (!idExists(_unitToUpdate.Id, m_allUnits.toArray(new Unit[m_allUnits.size()]))) {
            return false;
        }

        ContentValues unitsUpdateValues = new ContentValues();
        unitsUpdateValues.put("title", _unitToUpdate.UnitText);
        if(_db.update("Units", unitsUpdateValues, "id = ?", new String[]{ _unitToUpdate.Id + "" }) == 0) {
            return false;
        }

        for (int currentIndex = 0; currentIndex < m_allUnits.size(); currentIndex++) {
            if (m_allUnits.get(currentIndex).Id == _unitToUpdate.Id) {
                m_allUnits.set(currentIndex, new Unit(_unitToUpdate));
            }
        }

        return true;
    }

    /**
     * Does not throw an exception if update of database fails. Check return value.
     * @param _productToUpdate The changed Product. Changing id is not recommended and may fail (if not another Product
     *                         will be overwritten).
     * @param _db An open connection to a writable database.
     * @return Whether update succeeded.
     */
    public boolean updateProduct(Product _productToUpdate, SQLiteDatabase _db) {
        if (_db == null || _productToUpdate == null || _productToUpdate.Title == null) {
            throw new IllegalArgumentException("A parameter was not valid.");
        }

        if (!idExists(_productToUpdate.Id, m_allProducts.toArray(new Product[m_allProducts.size()]))) {
            return false;
        }

        ContentValues updatedProductValues = new ContentValues();
        updatedProductValues.put("title", _productToUpdate.Title);
        updatedProductValues.put("defaultvalue", _productToUpdate.DefaultValue);
        if (_productToUpdate.UnitId == INVALID_ID) {
            updatedProductValues.putNull("unit_id");
        } else {
            updatedProductValues.put("unit_id", _productToUpdate.UnitId);
        }
        if (_db.update("Products", updatedProductValues, "id=?", new String[]{ _productToUpdate.Id + "" }) == 0) {
            return false;
        }

        for (int currentIndex = 0; currentIndex < m_allProducts.size(); currentIndex++) {
            if (m_allProducts.get(currentIndex).Id == _productToUpdate.Id) {
                m_allProducts.set(currentIndex, new Product(_productToUpdate));
                break;
            }
        }

        return true;
    }

    /**
     * Does not throw an exception if update of database fails. Check return value.
     * @param _shoppingListToUpdate The changed list. Changing id is not recommended and may fail (if not, another
     *                              ShoppingList will be overwritten).
     * @param _db An open connection to a writable database.
     * @return Whether update succeeded.
     */
    public boolean updateShoppingList(ShoppingList _shoppingListToUpdate, SQLiteDatabase _db) {
        if (_db == null || _shoppingListToUpdate == null || _shoppingListToUpdate.Title == null ||
                _shoppingListToUpdate.ListEntries == null) {
            throw new IllegalArgumentException("A parameter was null or not valid. This is not allowed.");
        }

        if (!idExists(_shoppingListToUpdate.Id, m_allLists.toArray(new ShoppingList[m_allLists.size()]))) {
            return false;
        }

        boolean dbUpdatedSuccessfully = true;
        ContentValues updatedListValues = new ContentValues();
        updatedListValues.put("title", _shoppingListToUpdate.Title);
        try {
            _db.beginTransaction();
            if(_db.update("ShoppingLists", updatedListValues, "id = ?",
                    new String[]{ _shoppingListToUpdate.Id + "" }) == 0) {
                throw new Exception();
            }
            _db.delete("ProductsInShoppingLists", "shoppinglist_id = ?", new String[]{ _shoppingListToUpdate.Id + "" });
            for (int currentProduct = 0; currentProduct < _shoppingListToUpdate.ListEntries.size(); currentProduct++) {
                ContentValues updatedItem = new ContentValues();
                updatedItem.put("shoppinglist_id", _shoppingListToUpdate.Id);
                updatedItem.put("product_id", _shoppingListToUpdate.ListEntries.keyAt(currentProduct));
                updatedItem.put("value", _shoppingListToUpdate.ListEntries.valueAt(currentProduct));
                _db.insertOrThrow("ProductsInShoppingLists", null, updatedItem);
            }
            _db.setTransactionSuccessful();
        } catch (Exception e) {
            dbUpdatedSuccessfully = false;
        } finally {
            _db.endTransaction();
        }

        if (!dbUpdatedSuccessfully) {
            return false;
        }

        for (int currentListIndex = 0; currentListIndex < m_allLists.size(); currentListIndex++) {
            if (m_allLists.get(currentListIndex).Id == _shoppingListToUpdate.Id) {
                m_allLists.set(currentListIndex, new ShoppingList(_shoppingListToUpdate));
                break;
            }
        }

        return true;
    }

    /**
     * Deletes a Unit and all Product's that depend on it. Does not throw anything unless _db is null.
     */
    public void deleteUnit(Unit _unitToDelete, SQLiteDatabase _db) {
        if (_db == null) {
            throw new IllegalArgumentException("Database must be an open and writable SQLiteDatabase");
        }

        if (_unitToDelete == null) {
            return;
        }

        LinkedList<Product> productsToDelete = new LinkedList<>();

        for (Product currentProduct : m_allProducts) {
            if (currentProduct.UnitId == _unitToDelete.Id) {
                productsToDelete.addLast(currentProduct);
            }
        }

        for (Product currentProductToDelete : productsToDelete) {
            deleteProduct(currentProductToDelete, _db);
        }

        for (int currentUnitIndex = 0; currentUnitIndex < m_allUnits.size(); currentUnitIndex++) {
            Unit currentUnit = m_allUnits.get(currentUnitIndex);
            if (currentUnit.Id == _unitToDelete.Id) {
                m_allUnits.remove(currentUnitIndex);
                break;
            }
        }

        _db.delete("Units", "id = ?", new String[]{ _unitToDelete.Id + "" });
    }

    /**
     * Removes the Product from all ShoppingList's and deletes it from database. Does not throw anything if both
     * parameters are not null.
     */
    public void deleteProduct(Product _productToDelete, SQLiteDatabase _db) {
        if (_db == null) {
            throw new IllegalArgumentException("Database must be an open and writable SQLiteDatabase");
        }

        if (_productToDelete == null) {
            return;
        }

        for (ShoppingList currentList : m_allLists) {
            currentList.ListEntries.delete(_productToDelete.Id);
        }

        for (int currentIndex = 0; currentIndex < m_allProducts.size(); currentIndex++) {
            if (m_allProducts.get(currentIndex).Id == _productToDelete.Id) {
                m_allProducts.remove(currentIndex);
                break;
            }
        }

        _db.delete("Products", "id = ?", new String[]{ _productToDelete.Id + "" });
    }

    public int getCountOfShoppingLists() {
        return m_allLists.size();
    }

    /**
     * Deletes a ShoppingList. Does not throw anything if saving did not work or List was not found. Only throws if a
     * parameter is null.
     */
    public void deleteShoppingList(ShoppingList _shoppingListToDelete, SQLiteDatabase _db) {
        if (_db == null) {
            throw new IllegalArgumentException("Database must be an open and writable SQLiteDatabase");
        }

        if (_shoppingListToDelete == null) {
            return;
        }

        for (int currentIndex = 0; currentIndex < m_allLists.size(); currentIndex++) {
            if (m_allLists.get(currentIndex).Id == _shoppingListToDelete.Id) {
                m_allLists.remove(currentIndex);
                break;
            }
        }

        _db.delete("ShoppingLists", "id = ?", new String[]{  _shoppingListToDelete.Id + "" });
    }

    static class DBOpenHelper extends SQLiteOpenHelper {
        public static final int CURRENT_DATABASE_VERSION = 1;

        public DBOpenHelper(Context _context, String _name, SQLiteDatabase.CursorFactory _cursorFactory, int _version) {
            super(_context, _name, _cursorFactory, _version);
        }

        @Override
        public void onCreate(SQLiteDatabase _db) {
            // rowid's are omitted because it's not possible to refer to them, so just wasting space in this case.
            _db.execSQL("CREATE TABLE Units (" +
                    "id INTEGER NOT NULL, " +
                    "title STRING NOT NULL, " +
                    "PRIMARY KEY (id)" +
                    ") WITHOUT ROWID");
            _db.execSQL("CREATE TABLE Products (" +
                    "id INTEGER NOT NULL, " +
                    "title STRING NOT NULL, " +
                    "defaultvalue REAL NOT NULL, " +
                    "unit_id INTEGER, " +
                    "PRIMARY KEY (id), " +
                    "FOREIGN KEY (unit_id) REFERENCES Units(id) ON UPDATE RESTRICT ON DELETE CASCADE " +
                    ") WITHOUT ROWID");
            _db.execSQL("CREATE TABLE ShoppingLists (" +
                    "id INTEGER NOT NULL," +
                    "title STRING NOT NULL, " +
                    "PRIMARY KEY (id)" +
                    ") WITHOUT ROWID");
            _db.execSQL("CREATE TABLE ProductsInShoppingLists (" +
                    "shoppinglist_id INTEGER NOT NULL, " +
                    "product_id INTEGER NOT NULL, " +
                    "value REAL NOT NULL, " +
                    "PRIMARY KEY (shoppinglist_id, product_id), " +
                    "FOREIGN KEY (shoppinglist_id) REFERENCES ShoppingLists(id) ON UPDATE RESTRICT ON DELETE CASCADE, " +
                    "FOREIGN KEY (product_id) REFERENCES Products(id) ON UPDATE RESTRICT ON DELETE CASCADE " +
                    ") WITHOUT ROWID");
        }

        @Override
        public void onUpgrade(SQLiteDatabase _db, int _oldVersion, int _newVersion) {
            // Currently there is only db version 1.
        }

        @Override
        public void onConfigure(SQLiteDatabase _db) {
            _db.rawQuery("PRAGMA foreign_keys = ON", new String[0]);
        }
    }

    public static ModelManager getInstance() {
        if (m_sInstance == null) {
            m_sInstance = new ModelManager();
        }

        return m_sInstance;
    }
}
