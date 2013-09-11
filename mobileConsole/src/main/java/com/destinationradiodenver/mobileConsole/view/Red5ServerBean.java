package com.destinationradiodenver.mobileConsole.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;
import javax.ejb.SessionContext;
import javax.ejb.Stateful;
import javax.enterprise.context.Conversation;
import javax.enterprise.context.ConversationScoped;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import com.destinationradiodenver.mobileStreaming.web.entity.Red5Server;
import com.destinationradiodenver.mobileStreaming.web.entity.Stream;

/**
 * Backing bean for Red5Server entities.
 * <p>
 * This class provides CRUD functionality for all Red5Server entities. It focuses
 * purely on Java EE 6 standards (e.g. <tt>&#64;ConversationScoped</tt> for
 * state management, <tt>PersistenceContext</tt> for persistence,
 * <tt>CriteriaBuilder</tt> for searches) rather than introducing a CRUD framework or
 * custom base class.
 */

@Named
@Stateful
@ConversationScoped
public class Red5ServerBean implements Serializable
{

   private static final long serialVersionUID = 1L;

   /*
    * Support creating and retrieving Red5Server entities
    */

   private Long id;

   public Long getId()
   {
      return this.id;
   }

   public void setId(Long id)
   {
      this.id = id;
   }

   private Red5Server red5Server;

   public Red5Server getRed5Server()
   {
      return this.red5Server;
   }
   
   public List<Stream> getStreamsForServer(Long id){
	   TypedQuery<Stream> tQ = entityManager.createQuery("select stream from Stream as stream where stream.server.id is :id", Stream.class)
			   .setParameter("id", id);
	   List<Stream> list = tQ.getResultList();
	   if(list==null){
		   return new ArrayList<Stream>();
	   }
	   return list;
	   
   }
   
   @Inject
   private Conversation conversation;

   @PersistenceContext(type = PersistenceContextType.EXTENDED)
   private EntityManager entityManager;

   public String create()
   {

      this.conversation.begin();
      return "create?faces-redirect=true";
   }

   public void retrieve()
   {

      if (FacesContext.getCurrentInstance().isPostback())
      {
         return;
      }

      if (this.conversation.isTransient())
      {
         this.conversation.begin();
      }

      if (this.id == null)
      {
         this.red5Server = this.example;
      }
      else
      {
         this.red5Server = findById(getId());
      }
   }

   public Red5Server findById(Long id)
   {

      return this.entityManager.find(Red5Server.class, id);
   }

   /*
    * Support updating and deleting Red5Server entities
    */

   public String update()
   {
      this.conversation.end();

      try
      {
         if (this.id == null)
         {
            this.entityManager.persist(this.red5Server);
            return "search?faces-redirect=true";
         }
         else
         {
            this.entityManager.merge(this.red5Server);
            return "view?faces-redirect=true&id=" + this.red5Server.getId();
         }
      }
      catch (Exception e)
      {
         FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(e.getMessage()));
         return null;
      }
   }

   public String delete()
   {
      this.conversation.end();

      try
      {
         this.entityManager.remove(findById(getId()));
         this.entityManager.flush();
         return "search?faces-redirect=true";
      }
      catch (Exception e)
      {
         FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(e.getMessage()));
         return null;
      }
   }

   /*
    * Support searching Red5Server entities with pagination
    */

   private int page;
   private long count;
   private List<Red5Server> pageItems;

   private Red5Server example = new Red5Server();

   public int getPage()
   {
      return this.page;
   }

   public void setPage(int page)
   {
      this.page = page;
   }

   public int getPageSize()
   {
      return 10;
   }

   public Red5Server getExample()
   {
      return this.example;
   }

   public void setExample(Red5Server example)
   {
      this.example = example;
   }

   public void search()
   {
      this.page = 0;
   }

   public void paginate()
   {

      CriteriaBuilder builder = this.entityManager.getCriteriaBuilder();

      // Populate this.count

      CriteriaQuery<Long> countCriteria = builder.createQuery(Long.class);
      Root<Red5Server> root = countCriteria.from(Red5Server.class);
      countCriteria = countCriteria.select(builder.count(root)).where(getSearchPredicates(root));
      this.count = this.entityManager.createQuery(countCriteria).getSingleResult();

      // Populate this.pageItems

      CriteriaQuery<Red5Server> criteria = builder.createQuery(Red5Server.class);
      root = criteria.from(Red5Server.class);
      TypedQuery<Red5Server> query = this.entityManager.createQuery(criteria.select(root).where(getSearchPredicates(root)));
      query.setFirstResult(this.page * getPageSize()).setMaxResults(getPageSize());
      this.pageItems = query.getResultList();
   }

   private Predicate[] getSearchPredicates(Root<Red5Server> root)
   {

      CriteriaBuilder builder = this.entityManager.getCriteriaBuilder();
      List<Predicate> predicatesList = new ArrayList<Predicate>();

      String name = this.example.getName();
      if (name != null && !"".equals(name))
      {
         predicatesList.add(builder.like(root.<String> get("name"), '%' + name + '%'));
      }
      String hostname = this.example.getHostname();
      if (hostname != null && !"".equals(hostname))
      {
         predicatesList.add(builder.like(root.<String> get("hostname"), '%' + hostname + '%'));
      }

      return predicatesList.toArray(new Predicate[predicatesList.size()]);
   }

   public List<Red5Server> getPageItems()
   {
      return this.pageItems;
   }

   public long getCount()
   {
      return this.count;
   }

   /*
    * Support listing and POSTing back Red5Server entities (e.g. from inside an
    * HtmlSelectOneMenu)
    */

   public List<Red5Server> getAll()
   {

      CriteriaQuery<Red5Server> criteria = this.entityManager.getCriteriaBuilder().createQuery(Red5Server.class);
      return this.entityManager.createQuery(criteria.select(criteria.from(Red5Server.class))).getResultList();
   }

   @Resource
   private SessionContext sessionContext;

   public Converter getConverter()
   {

      final Red5ServerBean ejbProxy = this.sessionContext.getBusinessObject(Red5ServerBean.class);

      return new Converter()
      {

         @Override
         public Object getAsObject(FacesContext context, UIComponent component, String value)
         {

            return ejbProxy.findById(Long.valueOf(value));
         }

         @Override
         public String getAsString(FacesContext context, UIComponent component, Object value)
         {

            if (value == null)
            {
               return "";
            }

            return String.valueOf(((Red5Server) value).getId());
         }
      };
   }

   /*
    * Support adding children to bidirectional, one-to-many tables
    */

   private Red5Server add = new Red5Server();

   public Red5Server getAdd()
   {
      return this.add;
   }

   public Red5Server getAdded()
   {
      Red5Server added = this.add;
      this.add = new Red5Server();
      return added;
   }
}