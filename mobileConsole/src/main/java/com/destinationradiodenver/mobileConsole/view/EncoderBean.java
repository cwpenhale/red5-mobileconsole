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

import com.destinationradiodenver.mobileStreaming.application.EncoderDispatcher;
import com.destinationradiodenver.mobileStreaming.application.Encoders;
import com.destinationradiodenver.mobileStreaming.application.Streams;
import com.destinationradiodenver.mobileStreaming.messages.EncoderDispatchMessage;
import com.destinationradiodenver.mobileStreaming.messages.EncoderDispatchMessage.Task;
import com.destinationradiodenver.mobileStreaming.web.entity.Encoder;
import com.destinationradiodenver.mobileStreaming.web.entity.MobileProfile;
import com.destinationradiodenver.mobileStreaming.web.entity.Stream;

/**
 * Backing bean for Encoder entities.
 * <p>
 * This class provides CRUD functionality for all Encoder entities. It focuses
 * purely on Java EE 6 standards (e.g. <tt>&#64;ConversationScoped</tt> for
 * state management, <tt>PersistenceContext</tt> for persistence,
 * <tt>CriteriaBuilder</tt> for searches) rather than introducing a CRUD framework or
 * custom base class.
 */

@Named
@Stateful
@ConversationScoped
public class EncoderBean implements Serializable {

   private static final long serialVersionUID = 1L;

   /*
    * Support creating and retrieving Encoder entities
    */

   private Long id;
   
   @Inject
   EncoderDispatcher encoderDispatcher;
   
   @Inject
   Streams streams;
   
   @Inject
   Encoders encoders;

   public void toggleEncoder(Long id){
	   Encoder tempEncoder = findById(id);
	   EncoderDispatchMessage edm = EncoderDispatchMessage.generateEncoderDispatchMessage(tempEncoder);
	   if(encoders.contains(tempEncoder)){
		   edm.setTask(Task.STOP_ENCODING);
	   }else{
		   edm.setTask(Task.START_ENCODING);
	   }
	   encoderDispatcher.dispatch(edm);
   }
   
   public void toggleRecording(Stream stream){
	   EncoderDispatchMessage edm = new EncoderDispatchMessage();
	   edm.setName("RECORDING");
	   edm.setUri(stream.getRtmpUri());
	   if(streams.getStreamRecordingStatus(stream)){
		   edm.setTask(Task.STOP_RECORDING);
	   }else{
		   edm.setTask(Task.START_RECORDING);
	   }
	   encoderDispatcher.dispatch(edm);
   }
   public boolean isStreamActive(){
	   return streams.contains(getEncoder().getStream());
   }
   
   public boolean isActive(){
	   return encoders.contains(getEncoder());
   }

   public Long getId()
   {
      return this.id;
   }

   public void setId(Long id)
   {
      this.id = id;
   }

   private Encoder encoder;

   public Encoder getEncoder()
   {
      return this.encoder;
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
         this.encoder = this.example;
      }
      else
      {
         this.encoder = findById(getId());
      }
   }

   public Encoder findById(Long id)
   {

      return this.entityManager.find(Encoder.class, id);
   }

   /*
    * Support updating and deleting Encoder entities
    */

   public String update()
   {
      this.conversation.end();

      try
      {
         if (this.id == null)
         {
            this.entityManager.persist(this.encoder);
            return "search?faces-redirect=true";
         }
         else
         {
            this.entityManager.merge(this.encoder);
            return "view?faces-redirect=true&id=" + this.encoder.getId();
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
    * Support searching Encoder entities with pagination
    */

   private int page;
   private long count;
   private List<Encoder> pageItems;

   private Encoder example = new Encoder();

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

   public Encoder getExample()
   {
      return this.example;
   }

   public void setExample(Encoder example)
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
      Root<Encoder> root = countCriteria.from(Encoder.class);
      countCriteria = countCriteria.select(builder.count(root)).where(getSearchPredicates(root));
      this.count = this.entityManager.createQuery(countCriteria).getSingleResult();

      // Populate this.pageItems

      CriteriaQuery<Encoder> criteria = builder.createQuery(Encoder.class);
      root = criteria.from(Encoder.class);
      TypedQuery<Encoder> query = this.entityManager.createQuery(criteria.select(root).where(getSearchPredicates(root)));
      query.setFirstResult(this.page * getPageSize()).setMaxResults(getPageSize());
      this.pageItems = query.getResultList();
   }

   private Predicate[] getSearchPredicates(Root<Encoder> root)
   {

      CriteriaBuilder builder = this.entityManager.getCriteriaBuilder();
      List<Predicate> predicatesList = new ArrayList<Predicate>();

      MobileProfile mobileProfile = this.example.getMobileProfile();
      if (mobileProfile != null)
      {
         predicatesList.add(builder.equal(root.get("mobileProfile"), mobileProfile));
      }

      return predicatesList.toArray(new Predicate[predicatesList.size()]);
   }

   public List<Encoder> getPageItems()
   {
      return this.pageItems;
   }

   public long getCount()
   {
      return this.count;
   }

   /*
    * Support listing and POSTing back Encoder entities (e.g. from inside an
    * HtmlSelectOneMenu)
    */

   public List<Encoder> getAll()
   {

      CriteriaQuery<Encoder> criteria = this.entityManager.getCriteriaBuilder().createQuery(Encoder.class);
      return this.entityManager.createQuery(criteria.select(criteria.from(Encoder.class))).getResultList();
   }

   @Resource
   private SessionContext sessionContext;

   public Converter getConverter()
   {

      final EncoderBean ejbProxy = this.sessionContext.getBusinessObject(EncoderBean.class);

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

            return String.valueOf(((Encoder) value).getId());
         }
      };
   }

   /*
    * Support adding children to bidirectional, one-to-many tables
    */

   private Encoder add = new Encoder();

   public Encoder getAdd()
   {
      return this.add;
   }

   public Encoder getAdded()
   {
      Encoder added = this.add;
      this.add = new Encoder();
      return added;
   }

}