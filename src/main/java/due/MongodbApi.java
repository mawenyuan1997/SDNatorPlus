package due;
import com.mongodb.*;

import java.net.UnknownHostException;

public class MongodbApi {
    private MongoClient mongoClient;
    private DB database;
    private String id;

    public MongodbApi(String agentId) {
        this.id = agentId;
        try {
            mongoClient = new MongoClient(new MongoClientURI("mongodb://localhost:27017"));
            database = mongoClient.getDB("mfgData");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void update(String dataType, String key, String value) {
        DBCollection collection = database.getCollection(dataType);
        BasicDBObject query = new BasicDBObject();
        query.put("id", id);
        if (collection.find(query).count() > 0) {
            BasicDBObject newDocument = new BasicDBObject();
            newDocument.put(key, value);
            BasicDBObject updateObject = new BasicDBObject();
            updateObject.put("$set", newDocument);
            collection.update(query, updateObject);
        } else {
            BasicDBObject document = new BasicDBObject();
            document.put("id", id);
            document.put(key, value);
            collection.insert(document);
        }
    }

    public DBObject read(String dataType, String agentId, String key) {
        assert id.equals("central controller");
        DBCollection collection = database.getCollection(dataType);
        BasicDBObject searchQuery = new BasicDBObject();
        searchQuery.put("id", agentId);
        DBCursor cursor = collection.find(searchQuery);
        if (cursor.hasNext()) {
            return cursor.next();
        } else {
            return null;
        }
    }
}
