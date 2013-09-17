package com.destinationradiodenver.mobileStreaming.web.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Version;

@Entity
public class Encoder implements Serializable
{

   /**
    *@author cpenhale 
    */
   private static final long serialVersionUID = -7477444349677862580L;
   @Id
   private @GeneratedValue(strategy = GenerationType.AUTO)
   @Column(name = "id", updatable = false, nullable = false)
   Long id = null;
   @Version
   private @Column(name = "version")
   int version = 0;

   @ManyToOne(fetch = FetchType.EAGER)
   private MobileProfile mobileProfile;

   @ManyToOne(fetch = FetchType.EAGER)
   private Stream stream;

   public Long getId()
   {
      return this.id;
   }

   public void setId(final Long id)
   {
      this.id = id;
   }

   public int getVersion()
   {
      return this.version;
   }

   public void setVersion(final int version)
   {
      this.version = version;
   }

   @Override
   public boolean equals(Object that)
   {
      if (this == that)
      {
         return true;
      }
      if (that == null)
      {
         return false;
      }
      if (getClass() != that.getClass())
      {
         return false;
      }
      if (id != null)
      {
         return id.equals(((Encoder) that).id);
      }
      return super.equals(that);
   }

   @Override
   public int hashCode()
   {
      if (id != null)
      {
         return id.hashCode();
      }
      return super.hashCode();
   }

   public MobileProfile getMobileProfile()
   {
      return this.mobileProfile;
   }

   public void setMobileProfile(final MobileProfile mobileProfile)
   {
      this.mobileProfile = mobileProfile;
   }

   public String toString()
   {
      String result = "";
      result += serialVersionUID;
      return result;
   }

   public Stream getStream()
   {
      return this.stream;
   }

   public void setStream(final Stream stream)
   {
      this.stream = stream;
   }

}