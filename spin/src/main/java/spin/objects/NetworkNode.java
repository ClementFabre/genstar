package spin.objects;

import java.util.HashSet;
import java.util.Set;

import core.metamodel.pop.APopulationEntity;

/** Node of the SpinNetwork, linked to a GosplEntity (an individual from population generation module)
 * TODO Doublon d'informations sur les lienconnectés avec la liste ici et l'objet spinNetwork.
 * @author Felix, FredA722
 */
public class NetworkNode {
	
	// Entity associated 
	private APopulationEntity entity;
	
	// Non initalisé volontairement.
	private Set<NetworkLink> links;
	
	private String id;
	
	
	/** Constructeur sans paramètre.
	 * 
	 */
	public NetworkNode(){
	}
	
	/** Constructeur de networkNode prenant une entité
	 * 
	 * @param entite
	 */
	public NetworkNode(APopulationEntity entite, String id){
		this();
		entity = entite;
		this.id = id;
	}
	
	public void defineLinkHash(HashSet<NetworkLink> links){
		this.links = links;
	}
	
	
	public void addLink(NetworkLink link){
		links.add(link);
	}
	
	public APopulationEntity getEntity() {
		return entity;
	}
	
	public String getId(){
		return id;
	}
	
	public Set<NetworkLink> getLinks(){
		return links;
	}
	
	public void removeLink(NetworkLink l){
		links.remove(l);
	}
	
	public boolean hasLink(NetworkLink l){
		return links.contains(l);
	}
	
	public Set<NetworkNode> getNeighbours(){
		Set<NetworkNode> neighb = new HashSet<NetworkNode>();
		for(NetworkLink l : links){
			if(this.equals(l.getFrom()))
				neighb.add(l.getTo());
			else
				neighb.add(l.getFrom());
		}
		return neighb;
	}
	
}

