/*
 * twitter-scraper-java.main
 * Copyright (C) 2025 c8ff
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.seeight.twitterscraper.impl.user;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import dev.seeight.twitterscraper.impl.Entry;
import dev.seeight.twitterscraper.impl.Url;
import dev.seeight.twitterscraper.util.GsonUtil;
import dev.seeight.twitterscraper.util.JsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class User extends Entry {
	public String restId;

	// legacy
	public String createdAt;
	public String rawDescription;
	public String description;
	public List<Url> descriptionUrls;

	public @Nullable User.Birthdate birthdate;

	public Url url;

	public int likedTweetsCount;
	public int followersCount;
	public int followingCount;
	public int mediaCount;
	public int tweetsCount;

	public int friendsCount;

	public String name;
	public String screenName;
	public @Nullable String location;
	public String[] pinnedTweetsIds;
	public boolean possiblySensitive;
	public boolean verified;
	public boolean blueVerified;
	@SerializedName("protected")
	public boolean isProtected;
	public String verifiedType;

	public @Nullable String profileImageUrl;
	public String profileBannerUrl;
	public ProfileImageShape profileImageShape;

	public boolean canDM;
	public boolean canMediaTag;

	public boolean following;
	public boolean followedBy;
	public boolean blocking;
	public boolean blockedBy;
	public boolean muting;
	public boolean notifications;
	public boolean wantRetweets;

	public boolean followRequestSent;
	public boolean hasGraduatedAccess;

    public static User fromJson(Gson gson, JsonObject obj, JsonHelper h) {
        User ref = GsonUtil.createObject(gson, User.class);

        h.set(obj);
        ref.restId = h.string("rest_id", null);

        JsonObject legacy;
        if (ref.restId == null) {
            ref.restId = h.string("id_str");
            legacy = obj;
        } else {
            //"legacy" was removed, but still look for it as a fallback
            if (h.set(obj).has("legacy")) {
                legacy = h.set(obj).next("legacy").object();
            } else {
                legacy = obj;
            }
        }

        // (inside 'root' object)
        h.set(obj);

        ref.blueVerified = h.bool("is_blue_verified", false);

        String s = h.string("profile_image_shape", "");
        switch (s) {
            case "Square" -> ref.profileImageShape = ProfileImageShape.SQUARE;
            case "Circle" -> ref.profileImageShape = ProfileImageShape.CIRCLE;
            default -> ref.profileImageShape = ProfileImageShape.UNKNOWN;
        }

        if (h.has("legacy_extended_profile") && h.next("legacy_extended_profile").has("birthdate")) {
            h.next("birthdate");
            var birthdate = new Birthdate();
            birthdate.day = h.integer("day", -1);
            birthdate.month = h.integer("month", -1);
            birthdate.year = h.integer("year", -1);
            birthdate.visibility = Birthdate.Visibility.from(h.string("visibility"));
            birthdate.yearVisibility = Birthdate.Visibility.from(h.string("year_visibility"));
            ref.birthdate = birthdate;
        }

        h.set(obj);
        if (h.has("avatar"))
            ref.profileImageUrl = h.set(obj).next("avatar").string("image_url", null);

        h.set(obj);
        ref.followRequestSent = h.bool("follow_request_sent", false);
        ref.hasGraduatedAccess = h.bool("has_graduated_access", false);

        if (h.set(obj).has("core"))
            h.set(obj).next("core");
        else h.set(legacy);

        ref.screenName = h.string("screen_name");
        ref.name = h.string("name", "[restricted]");
        ref.createdAt = h.string("created_at");

        // what the fuck
        if (h.set(obj).tryNext("relationship_perspectives")) {
            ref.following = h.bool("following");
            ref.followedBy = h.bool("followed_by", false);
            ref.blocking = h.bool("blocking", false);
            ref.blockedBy = h.bool("blocked_by", false);
            ref.muting = h.bool("muting", false);
        }

        // what??
        if (h.set(obj).tryNext("verification")) ref.verified = h.bool("verified");
        if (h.set(obj).tryNext("privacy")) ref.isProtected = h.bool("protected");
        if (h.set(obj).tryNext("location") && h.has("location")) ref.location = h.string("location");

        // Resolve description and entities (profile_bio vs legacy description)
        h.set(obj);
        JsonObject entities = null;
        //new format has profile info in "profile_bio" now.
        if (h.has("profile_bio")) {
            h.next("profile_bio");
            ref.rawDescription = ref.description = h.string("description");
            if (h.has("entities")) {
                entities = h.object("entities");
            }
        } else {
            h.set(legacy);
            ref.rawDescription = ref.description = h.string("description");
            if (h.has("entities")) {
                entities = h.object("entities");
            }
        }

        ref.descriptionUrls = Collections.emptyList();

        if (entities != null) {
            h.set(entities);
            if (h.tryNext("description")) {
                JsonArray v = h.has("urls") ? h.array("urls") : new JsonArray();
                for (JsonElement elm : v) {
                    if (!(elm instanceof JsonObject o)) {
                        continue;
                    }

                    if (ref.descriptionUrls == Collections.<Url>emptyList()) {
                        ref.descriptionUrls = new ArrayList<>();
                    }

                    Url u = Url.fromJson(o, h);
                    ref.descriptionUrls.add(u);

                    String url = u.url;
                    String originalUrl = u.expandedUrl;
                    ref.description = ref.description.replace(url, originalUrl);
                }
            }

            // Define URL in bio
            if (h.set(entities).has("url")) {
                try {
                    var urlsArray = h.set(entities).next("url").next("urls");
                    if (!urlsArray.array().isEmpty()) {
                        ref.url = Url.fromJson(urlsArray.get(0).object(), h);
                    }
                } catch (Exception ignored) {}
            }
        }

        // counts
        if (h.set(obj).has("action_counts")) {
            h.set(obj).next("action_counts");
            ref.likedTweetsCount = h.integer("favorites_count");
        } else {
            ref.likedTweetsCount = h.set(legacy).integer("favourites_count");
        }

        if (h.set(obj).has("relationship_counts")) {
            h.set(obj).next("relationship_counts");
            ref.followersCount = h.integer("followers");
            ref.followingCount = h.integer("following");
            ref.friendsCount = ref.followingCount;
        } else {
            ref.followersCount = h.set(legacy).integer("followers_count");
            ref.followingCount = h.set(legacy).integer("friends_count");
            ref.friendsCount = h.set(legacy).integer("friends_count");
        }

        if (h.set(obj).has("tweet_counts")) {
            h.set(obj).next("tweet_counts");
            ref.mediaCount = h.integer("media_tweets", -1);
            ref.tweetsCount = h.integer("tweets");
        } else {
            ref.mediaCount = h.set(legacy).integer("media_count", -1);
            ref.tweetsCount = h.set(legacy).integer("statuses_count");
        }

        // Info
        if (h.set(obj).has("banner")) {
            ref.profileBannerUrl = h.set(obj).next("banner").string("image_url", null);
        } else {
            ref.profileBannerUrl = h.set(legacy).string("profile_banner_url", null);
        }

        if (h.set(obj).has("pinned_items")) {
            ref.pinnedTweetsIds = h.set(obj).next("pinned_items").stringArray("tweet_ids_str", new String[0]);
        } else {
            ref.pinnedTweetsIds = h.set(legacy).stringArray("pinned_tweet_ids_str", new String[0]);
        }

        if (h.set(obj).has("possibly_sensitive")) {
            ref.possiblySensitive = h.bool("possibly_sensitive", false);
        } else if (h.set(legacy).has("possibly_sensitive")) {
            ref.possiblySensitive = h.set(legacy).bool("possibly_sensitive", false);
        } else if (h.set(legacy).has("status")) {
            ref.possiblySensitive = h.set(legacy).next("status").bool("possibly_sensitive", false);
        }

        // user specific info
        if (h.set(obj).has("dm_permissions")) {
            ref.canDM = h.set(obj).next("dm_permissions").bool("can_dm", false);
        } else {
            ref.canDM = h.set(legacy).bool("can_dm", false);
        }

        if (h.set(obj).has("media_permissions")) {
            ref.canMediaTag = h.set(obj).next("media_permissions").bool("can_media_tag", false);
        } else {
            ref.canMediaTag = h.set(legacy).bool("can_media_tag", false);
        }

        if (h.set(obj).has("notifications_settings")) {
            ref.notifications = h.set(obj).next("notifications_settings").bool("notifications_enabled", false);
        } else {
            ref.notifications = h.set(legacy).bool("notifications", false);
        }

        ref.wantRetweets = h.set(legacy).bool("want_retweets", false);

        return ref;
    }

	public static class Birthdate {
		public int day;
		public int month;
		public int year;
		public Visibility visibility;
		public Visibility yearVisibility;

		public enum Visibility {
			Public,
			Followers,
			Following,
			MutualFollow,
			Self,
			;

			static Visibility from(String str) {
				for (Visibility value : values()) {
					if (value.name().equalsIgnoreCase(str)) {
						return value;
					}
				}

				throw new RuntimeException("Unknown visibility type: " + str);
			}
		}
	}
}
