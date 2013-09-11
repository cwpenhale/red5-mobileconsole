package com.destinationradiodenver.mobileStreaming.web.entity;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Version;

import com.destinationradiodenver.mobileStreaming.web.entity.Encoder;
import com.destinationradiodenver.mobileStreaming.web.entity.Red5Server;
import javax.persistence.OneToMany;
import javax.persistence.CascadeType;

@Entity
public class Stream implements Serializable
{

   /**
    * @author cpenhale
    */
   private static final long serialVersionUID = -2274658239735869393L;
   @Id
   private @GeneratedValue(strategy = GenerationType.AUTO)
   @Column(name = "id", updatable = false, nullable = false)
   Long id = null;
   @Version
   private @Column(name = "version")
   int version = 0;

   @Column
   private String friendlyName;

   @Column
   private String description;

   @Column
   private String rtmpUri;

   @ManyToMany
   private Set<MobileProfile> mobileProfiles = new HashSet<MobileProfile>();

   @ManyToOne
   private Red5Server server;

   private @OneToMany(mappedBy = "stream", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
   Set<Encoder> encoders = new HashSet<Encoder>();

   @Column
   private boolean automaticallyStartEncoders;

   @Column
   private int restartEncodersEveryMinutes;

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
         return id.equals(((Stream) that).id);
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

   public String getFriendlyName()
   {
      return this.friendlyName;
   }

   public void setFriendlyName(final String friendlyName)
   {
      this.friendlyName = friendlyName;
   }

   public String getDescription()
   {
      return this.description;
   }

   public void setDescription(final String description)
   {
      this.description = description;
   }

   public String getRtmpUri()
   {
      return this.rtmpUri;
   }

   public void setRtmpUri(final String rtmpUri)
   {
      this.rtmpUri = rtmpUri;
   }

   public Set<MobileProfile> getMobileProfiles()
   {
      return this.mobileProfiles;
   }

   public void setMobileProfiles(final Set<MobileProfile> mobileProfiles)
   {
      this.mobileProfiles = mobileProfiles;
   }

   public Red5Server getServer()
   {
      return this.server;
   }

   public void setServer(final Red5Server server)
   {
      this.server = server;
   }

   public Set<Encoder> getEncoders()
   {
      return this.encoders;
   }

   public void setEncoders(final Set<Encoder> encoders)
   {
      this.encoders = encoders;
   }

   public boolean getAutomaticallyStartEncoders()
   {
      return this.automaticallyStartEncoders;
   }

   public void setAutomaticallyStartEncoders(final boolean automaticallyStartEncoders)
   {
      this.automaticallyStartEncoders = automaticallyStartEncoders;
   }

   public int getRestartEncodersEveryMinutes()
   {
      return this.restartEncodersEveryMinutes;
   }

   public void setRestartEncodersEveryMinutes(final int restartEncodersEveryMinutes)
   {
      this.restartEncodersEveryMinutes = restartEncodersEveryMinutes;
   }

   public String toString()
   {
      String result = "";
      result += serialVersionUID;
      if (friendlyName != null && !friendlyName.trim().isEmpty())
         result += " " + friendlyName;
      if (description != null && !description.trim().isEmpty())
         result += " " + description;
      if (rtmpUri != null && !rtmpUri.trim().isEmpty())
         result += " " + rtmpUri;
      result += " " + automaticallyStartEncoders;
      result += " " + restartEncodersEveryMinutes;
      return result;
   }
   
}