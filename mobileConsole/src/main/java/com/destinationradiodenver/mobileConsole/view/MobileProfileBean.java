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

import com.destinationradiodenver.mobileStreaming.web.entity.MobileProfile;

/**
 * Backing bean for MobileProfile entities.
 * <p>
 * This class provides CRUD functionality for all MobileProfile entities. It focuses
 * purely on Java EE 6 standards (e.g. <tt>&#64;ConversationScoped</tt> for
 * state management, <tt>PersistenceContext</tt> for persistence,
 * <tt>CriteriaBuilder</tt> for searches) rather than introducing a CRUD framework or
 * custom base class.
 */

@Named
@Stateful
@ConversationScoped
public class MobileProfileBean implements Serializable
{

   private static final long serialVersionUID = 1L;

   /*
    * Support creating and retrieving MobileProfile entities
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

   private MobileProfile mobileProfile;

   public MobileProfile getMobileProfile()
   {
      return this.mobileProfile;
   }

   @Inject
   private Conversation conversation;

   @PersistenceContext
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
         this.mobileProfile = this.example;
      }
      else
      {
         this.mobileProfile = findById(getId());
      }
   }

   public MobileProfile findById(Long id)
   {

      return this.entityManager.find(MobileProfile.class, id);
   }

   /*
    * Support updating and deleting MobileProfile entities
    */

   public String update()
   {
      this.conversation.end();

      try
      {
         if (this.id == null)
         {
            this.entityManager.persist(this.mobileProfile);
            return "search?faces-redirect=true";
         }
         else
         {
            this.entityManager.merge(this.mobileProfile);
            return "view?faces-redirect=true&id=" + this.mobileProfile.getId();
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
    * Support searching MobileProfile entities with pagination
    */

   private int page;
   private long count;
   private List<MobileProfile> pageItems;

   private MobileProfile example = new MobileProfile();

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

   public MobileProfile getExample()
   {
      return this.example;
   }

   public void setExample(MobileProfile example)
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
      Root<MobileProfile> root = countCriteria.from(MobileProfile.class);
      countCriteria = countCriteria.select(builder.count(root)).where(getSearchPredicates(root));
      this.count = this.entityManager.createQuery(countCriteria).getSingleResult();

      // Populate this.pageItems

      CriteriaQuery<MobileProfile> criteria = builder.createQuery(MobileProfile.class);
      root = criteria.from(MobileProfile.class);
      TypedQuery<MobileProfile> query = this.entityManager.createQuery(criteria.select(root).where(getSearchPredicates(root)));
      query.setFirstResult(this.page * getPageSize()).setMaxResults(getPageSize());
      this.pageItems = query.getResultList();
   }

   private Predicate[] getSearchPredicates(Root<MobileProfile> root)
   {

      CriteriaBuilder builder = this.entityManager.getCriteriaBuilder();
      List<Predicate> predicatesList = new ArrayList<Predicate>();

      String name = this.example.getName();
      if (name != null && !"".equals(name))
      {
         predicatesList.add(builder.like(root.<String> get("name"), '%' + name + '%'));
      }
      int bandwidth = this.example.getBandwidth();
      if (bandwidth != 0)
      {
         predicatesList.add(builder.equal(root.get("bandwidth"), bandwidth));
      }
      int width = this.example.getWidth();
      if (width != 0)
      {
         predicatesList.add(builder.equal(root.get("width"), width));
      }
      int height = this.example.getHeight();
      if (height != 0)
      {
         predicatesList.add(builder.equal(root.get("height"), height));
      }

      return predicatesList.toArray(new Predicate[predicatesList.size()]);
   }

   public List<MobileProfile> getPageItems()
   {
      return this.pageItems;
   }

   public long getCount()
   {
      return this.count;
   }

   /*
    * Support listing and POSTing back MobileProfile entities (e.g. from inside an
    * HtmlSelectOneMenu)
    */

   public List<MobileProfile> getAll()
   {

      CriteriaQuery<MobileProfile> criteria = this.entityManager.getCriteriaBuilder().createQuery(MobileProfile.class);
      return this.entityManager.createQuery(criteria.select(criteria.from(MobileProfile.class))).getResultList();
   }

   @Resource
   private SessionContext sessionContext;

   public Converter getConverter()
   {

      final MobileProfileBean ejbProxy = this.sessionContext.getBusinessObject(MobileProfileBean.class);

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

            return String.valueOf(((MobileProfile) value).getId());
         }
      };
   }

   /*
    * Support adding children to bidirectional, one-to-many tables
    */

   private MobileProfile add = new MobileProfile();

   public MobileProfile getAdd()
   {
      return this.add;
   }

   public MobileProfile getAdded()
   {
      MobileProfile added = this.add;
      this.add = new MobileProfile();
      return added;
   }
}