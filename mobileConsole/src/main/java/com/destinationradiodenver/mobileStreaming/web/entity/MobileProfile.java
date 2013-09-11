package com.destinationradiodenver.mobileStreaming.web.entity;

import javax.persistence.Entity;
import java.io.Serializable;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;
import javax.persistence.Version;
import java.lang.Override;
import java.util.Set;
import java.util.HashSet;

import javax.persistence.ManyToMany;

import com.destinationradiodenver.mobileStreaming.web.entity.Encoder;
import com.destinationradiodenver.mobileStreaming.web.entity.Stream;

import javax.persistence.OneToMany;
import javax.persistence.CascadeType;

@Entity
public class MobileProfile implements Serializable
{

   /**
    *@author cpenhale 
    */
   private static final long serialVersionUID = 3777485706873502024L;
   @Id
   private @GeneratedValue(strategy = GenerationType.AUTO)
   @Column(name = "id", updatable = false, nullable = false)
   Long id = null;
   @Version
   private @Column(name = "version")
   int version = 0;

   @Column
   private String name;

   @Column
   private int bandwidth;

   @Column
   private int width;

   @Column
   private int height;

   private @ManyToMany(mappedBy = "mobileProfiles")
   Set<Stream> streams = new HashSet<Stream>();

   private @OneToMany(mappedBy = "mobileProfile", cascade = CascadeType.ALL)
   Set<Encoder> encoders = new HashSet<Encoder>();

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
         return id.equals(((MobileProfile) that).id);
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

   public String getName()
   {
      return this.name;
   }

   public void setName(final String name)
   {
      this.name = name;
   }

   public int getBandwidth()
   {
      return this.bandwidth;
   }

   public void setBandwidth(final int bandwidth)
   {
      this.bandwidth = bandwidth;
   }

   public int getWidth()
   {
      return this.width;
   }

   public void setWidth(final int width)
   {
      this.width = width;
   }

   public int getHeight()
   {
      return this.height;
   }

   public void setHeight(final int height)
   {
      this.height = height;
   }

   public String toString()
   {
      String result = "";
      if (name != null && !name.trim().isEmpty())
         result += name;
      result += " " + bandwidth;
      result += " " + width;
      result += " " + height;
      return result;
   }

   public Set<Stream> getStreams()
   {
      return this.streams;
   }

   public void setStreams(final Set<Stream> streams)
   {
      this.streams = streams;
   }

   public Set<Encoder> getEncoders()
   {
      return this.encoders;
   }

   public void setEncoders(final Set<Encoder> encoders)
   {
      this.encoders = encoders;
   }

}