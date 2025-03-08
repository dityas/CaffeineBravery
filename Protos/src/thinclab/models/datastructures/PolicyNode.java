/*
 *	THINC Lab at UGA | Cyber Deception Group
 *
 *	Author: Aditya Shinde
 * 
 *	email: shinde.aditya386@gmail.com
 */
package thinclab.models.datastructures;

import java.io.Serializable;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import thinclab.utils.Jsonable;

/*
 * @author adityas
 *
 */
public class PolicyNode implements Jsonable, Serializable {

	public String actName;
	public int alphaId;
	public int actId;
    public int nodeId = -99;
    public boolean start;
	
	public PolicyNode(int alphaId, int actId, String actName) {

		this.alphaId = alphaId; 
		this.actId = actId;
		this.actName = actName;
        this.start = false;
	}
	
    public PolicyNode(int alphaId, int actId, String actName, boolean start) {

		this.alphaId = alphaId; 
		this.actId = actId;
		this.actName = actName;
        this.start = start;
	}
	
    @Override
	public String toString() {
		
		return new GsonBuilder().create().toJson(toJson());
	}

	@Override
	public boolean equals(Object obj) {

		if (!(obj instanceof PolicyNode))
			return false;

		var node = (PolicyNode) obj;

		if (node.alphaId == alphaId && node.actId == actId 
                && node.actName.contentEquals(actName)
                && node.nodeId == nodeId)
			return true;

		return false;
	}

	@Override
	public int hashCode() {

		var builder = new HashCodeBuilder();
		builder.append(alphaId).append(actId).append(actName).append(nodeId);

		return builder.toHashCode();
	}

	@Override
	public JsonObject toJson() {

		var json = new JsonObject();
		json.addProperty("alpha", alphaId);
		json.addProperty("actId", actId);
		json.addProperty("nodeId", nodeId);
		json.addProperty("act", actName);

        if (start)
		    json.addProperty("start", start);
		
		return json;
	}

}
