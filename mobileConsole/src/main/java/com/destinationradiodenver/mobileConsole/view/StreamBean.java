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

import com.destinationradiodenver.mobileStreaming.application.Streams;
import com.destinationradiodenver.mobileStreaming.web.entity.Encoder;
import com.destinationradiodenver.mobileStreaming.web.entity.Stream;
import com.destinationradiodenver.mobileStreaming.web.entity.Red5Server;

/**
 * Backing bean for Stream entities.
 * <p>
 * This class provides CRUD functionality for all Stream entities. It focuses
 * purely on Java EE 6 standards (e.g. <tt>&#64;ConversationScoped</tt> for
 * state management, <tt>PersistenceContext</tt> for persistence,
 * <tt>CriteriaBuilder</tt> for searches) rather than introducing a CRUD framework or
 * custom base class.
 */

@Named
@Stateful
@ConversationScoped
public class StreamBean implements Serializable
{

   private static final long serialVersionUID = 1L;

   /*
    * Support creating and retrieving Stream entities
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

   private Stream stream;

   public Stream getStream()
   {
      return this.stream;
   }
   
   public List<Encoder> getEncodersForStream(Long id){
	   TypedQuery<Encoder> tQ = entityManager.createQuery("select encoder from Encoder as encoder where encoder.stream.id is :id", Encoder.class)
			   .setParameter("id", id);
	   List<Encoder> list = tQ.getResultList();
	   if(list==null){
		   return new ArrayList<Encoder>();
	   }
	   return list;
	   
   }
   @Inject
   private Conversation conversation;
   
   @Inject
   private Streams streams;
   
   @PersistenceContext(type = PersistenceContextType.EXTENDED)
   private EntityManager entityManager;
   
   public boolean active;
   
   public boolean isActive(){
	   return streams.contains(getStream());
   }
   
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
         this.stream = this.example;
      }
      else
      {
         this.stream = findById(getId());
      }
   }

   public Stream findById(Long id)
   {

      return this.entityManager.find(Stream.class, id);
   }

   /*
    * Support updating and deleting Stream entities
    */

   public String update()
   {
      this.conversation.end();

      try
      {
         if (this.id == null)
         {
            this.entityManager.persist(this.stream);
            return "search?faces-redirect=true";
         }
         else
         {
            this.entityManager.merge(this.stream);
            return "view?faces-redirect=true&id=" + this.stream.getId();
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
    * Support searching Stream entities with pagination
    */

   private int page;
   private long count;
   private List<Stream> pageItems;

   private Stream example = new Stream();

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

   public Stream getExample()
   {
      return this.example;
   }

   public void setExample(Stream example)
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
      Root<Stream> root = countCriteria.from(Stream.class);
      countCriteria = countCriteria.select(builder.count(root)).where(getSearchPredicates(root));
      this.count = this.entityManager.createQuery(countCriteria).getSingleResult();

      // Populate this.pageItems

      CriteriaQuery<Stream> criteria = builder.createQuery(Stream.class);
      root = criteria.from(Stream.class);
      TypedQuery<Stream> query = this.entityManager.createQuery(criteria.select(root).where(getSearchPredicates(root)));
      query.setFirstResult(this.page * getPageSize()).setMaxResults(getPageSize());
      this.pageItems = query.getResultList();
   }

   private Predicate[] getSearchPredicates(Root<Stream> root)
   {

      CriteriaBuilder builder = this.entityManager.getCriteriaBuilder();
      List<Predicate> predicatesList = new ArrayList<Predicate>();

      String friendlyName = this.example.getFriendlyName();
      if (friendlyName != null && !"".equals(friendlyName))
      {
         predicatesList.add(builder.like(root.<String> get("friendlyName"), '%' + friendlyName + '%'));
      }
      String description = this.example.getDescription();
      if (description != null && !"".equals(description))
      {
         predicatesList.add(builder.like(root.<String> get("description"), '%' + description + '%'));
      }
      String rtmpUri = this.example.getRtmpUri();
      if (rtmpUri != null && !"".equals(rtmpUri))
      {
         predicatesList.add(builder.like(root.<String> get("rtmpUri"), '%' + rtmpUri + '%'));
      }
      Red5Server server = this.example.getServer();
      if (server != null)
      {
         predicatesList.add(builder.equal(root.get("server"), server));
      }

      return predicatesList.toArray(new Predicate[predicatesList.size()]);
   }

   public List<Stream> getPageItems()
   {
      return this.pageItems;
   }

   public long getCount()
   {
      return this.count;
   }

   /*
    * Support listing and POSTing back Stream entities (e.g. from inside an
    * HtmlSelectOneMenu)
    */

   public List<Stream> getAll()
   {

      CriteriaQuery<Stream> criteria = this.entityManager.getCriteriaBuilder().createQuery(Stream.class);
      return this.entityManager.createQuery(criteria.select(criteria.from(Stream.class))).getResultList();
   }

   @Resource
   private SessionContext sessionContext;

   public Converter getConverter()
   {

      final StreamBean ejbProxy = this.sessionContext.getBusinessObject(StreamBean.class);

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

            return String.valueOf(((Stream) value).getId());
         }
      };
   }

   /*
    * Support adding children to bidirectional, one-to-many tables
    */

   private Stream add = new Stream();

   public Stream getAdd()
   {
      return this.add;
   }

   public Stream getAdded()
   {
      Stream added = this.add;
      this.add = new Stream();
      return added;
   }
}