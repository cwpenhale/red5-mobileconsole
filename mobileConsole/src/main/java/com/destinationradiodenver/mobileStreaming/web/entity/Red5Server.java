package com.destinationradiodenver.mobileStreaming.web.entity;

import javax.persistence.Entity;
import java.io.Serializable;

import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;
import javax.persistence.Version;
import java.lang.Override;

import com.destinationradiodenver.mobileStreaming.web.entity.Stream;

import java.util.Set;
import java.util.HashSet;
import javax.persistence.OneToMany;
import javax.persistence.CascadeType;

@Entity
public class Red5Server implements Serializable
{

   /**
    * @author cpenhale
    */
   private static final long serialVersionUID = 7573682514186348298L;
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
   private String hostname;

   private @OneToMany(mappedBy = "server", cascade = CascadeType.ALL, fetch=FetchType.EAGER)
   Set<Stream> streams = new HashSet<Stream>();

   @Column
   private boolean enabled;

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
         return id.equals(((Red5Server) that).id);
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

   public String getHostname()
   {
      return this.hostname;
   }

   public void setHostname(final String hostname)
   {
      this.hostname = hostname;
   }

   public Set<Stream> getStreams()
   {
      return this.streams;
   }

   public void setStreams(final Set<Stream> streams)
   {
      this.streams = streams;
   }

   public boolean getEnabled()
   {
      return this.enabled;
   }

   public void setEnabled(final boolean enabled)
   {
      this.enabled = enabled;
   }

   public String toString()
   {
      String result = "";
      result += serialVersionUID;
      if (name != null && !name.trim().isEmpty())
         result += " " + name;
      if (hostname != null && !hostname.trim().isEmpty())
         result += " " + hostname;
      result += " " + enabled;
      return result;
   }

}