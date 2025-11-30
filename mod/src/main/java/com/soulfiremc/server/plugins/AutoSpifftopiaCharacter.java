/*
 * SoulFire
 * Copyright (C) 2024  AlexProgrammerDE
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.soulfiremc.server.plugins;

import com.soulfiremc.server.api.InternalPlugin;
import com.soulfiremc.server.api.InternalPluginClass;
import com.soulfiremc.server.api.PluginInfo;
import com.soulfiremc.server.api.event.bot.BotDisconnectedEvent;
import com.soulfiremc.server.api.event.bot.BotPostTickEvent;
import com.soulfiremc.server.api.event.lifecycle.InstanceSettingsRegistryInitEvent;
import com.soulfiremc.server.bot.BotConnection;
import com.soulfiremc.server.settings.lib.SettingsObject;
import com.soulfiremc.server.settings.property.*;
import com.soulfiremc.server.util.SFHelpers;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.lenni0451.lambdaevents.EventHandler;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@InternalPluginClass
public final class AutoSpifftopiaCharacter extends InternalPlugin {
  private static final Set<BotConnection> EXECUTED_CONNECTIONS = ConcurrentHashMap.newKeySet();
  private static final String VOWELS = "aeiouy";
  private static final String CONSONANTS = "bcdfghjklmnpqrstvwxz";

  public AutoSpifftopiaCharacter() {
    super(new PluginInfo(
      "auto-spifftopia-character",
      "1.0.0",
      "Automatically creates and selects a character when bots connect to Spifftopia lobby",
      "AlexProgrammerDE",
      "GPL-3.0",
      "https://soulfiremc.com"
    ));
  }

  @EventHandler
  public static void onTick(BotPostTickEvent event) {
    var connection = event.connection();
    var settingsSource = connection.settingsSource();

    if (!settingsSource.get(AutoSpifftopiaCharacterSettings.ENABLED)) {
      return;
    }

    // Only execute once per connection
    if (EXECUTED_CONNECTIONS.contains(connection)) {
      return;
    }

    var player = connection.minecraft().player;
    if (player == null) {
      return;
    }

    // Mark as executed immediately to prevent duplicate execution
    EXECUTED_CONNECTIONS.add(connection);

    // Schedule initial delay before sending commands
    connection.scheduler().schedule(
      () -> {
        if (connection.isDisconnected()) {
          return;
        }

        // Generate valid random name
        var firstName = generateValidName(settingsSource.getRandom(AutoSpifftopiaCharacterSettings.FIRST_NAME_LENGTH).getAsInt());
        var lastName = generateValidName(settingsSource.getRandom(AutoSpifftopiaCharacterSettings.LAST_NAME_LENGTH).getAsInt());

        // Ensure combined length doesn't exceed 15 characters
        while ((firstName + lastName).length() > 15) {
          if (firstName.length() > lastName.length()) {
            firstName = generateValidName(Math.max(3, firstName.length() - 1));
          } else {
            lastName = generateValidName(Math.max(3, lastName.length() - 1));
          }
        }

        // Send createcharacter command
        connection.sendChatMessage("/createcharacter " + firstName + " " + lastName);

        // Schedule selectcharacter command after delay
        connection.scheduler().schedule(
          () -> {
            if (connection.isDisconnected()) {
              return;
            }
            connection.sendChatMessage("/selectcharacter");
          },
          settingsSource.getRandom(AutoSpifftopiaCharacterSettings.COMMAND_DELAY).getAsLong(),
          TimeUnit.SECONDS
        );
      },
      settingsSource.getRandom(AutoSpifftopiaCharacterSettings.INITIAL_DELAY).getAsLong(),
      TimeUnit.SECONDS
    );
  }

  private static String generateValidName(int length) {
    // Ensure length is within valid range
    length = Math.max(3, Math.min(16, length));

    var random = ThreadLocalRandom.current();
    var name = new StringBuilder(length);
    var lastChar = '\0';
    var consonantRun = 0;
    var sameLetterRun = 1;

    // Generate name with alternating pattern to avoid gibberish
    // Ensure: at least one vowel, no 3+ same letters, no 5+ consonants
    for (int i = 0; i < length; i++) {
      char ch;
      boolean isVowel;
      
      if (i == 0) {
        // Start with consonant
        ch = CONSONANTS.charAt(random.nextInt(CONSONANTS.length()));
        isVowel = false;
        consonantRun = 1;
      } else {
        // Alternate pattern but ensure we don't violate rules
        boolean needVowel = consonantRun >= 4 || (i >= length - 1 && consonantRun == length - 1);
        
        if (needVowel) {
          ch = VOWELS.charAt(random.nextInt(VOWELS.length()));
          isVowel = true;
          consonantRun = 0;
        } else if (i % 2 == 0) {
          ch = CONSONANTS.charAt(random.nextInt(CONSONANTS.length()));
          isVowel = false;
          consonantRun++;
        } else {
          ch = VOWELS.charAt(random.nextInt(VOWELS.length()));
          isVowel = true;
          consonantRun = 0;
        }
      }

      // Avoid 3+ consecutive same letters
      if (ch == lastChar) {
        sameLetterRun++;
        if (sameLetterRun >= 3) {
          // Force a different character
          if (isVowel) {
            ch = CONSONANTS.charAt(random.nextInt(CONSONANTS.length()));
            isVowel = false;
            consonantRun++;
          } else {
            ch = VOWELS.charAt(random.nextInt(VOWELS.length()));
            isVowel = true;
            consonantRun = 0;
          }
          sameLetterRun = 1;
        }
      } else {
        sameLetterRun = 1;
      }

      name.append(ch);
      lastChar = ch;
    }

    // Ensure we have at least one vowel
    boolean hasVowel = false;
    for (int i = 0; i < name.length(); i++) {
      if (VOWELS.indexOf(Character.toLowerCase(name.charAt(i))) >= 0) {
        hasVowel = true;
        break;
      }
    }
    
    if (!hasVowel && name.length() > 0) {
      // Replace a consonant with a vowel
      int replaceIndex = random.nextInt(name.length());
      name.setCharAt(replaceIndex, VOWELS.charAt(random.nextInt(VOWELS.length())));
    }

    // Capitalize first letter
    if (name.length() > 0) {
      name.setCharAt(0, Character.toUpperCase(name.charAt(0)));
    }

    return name.toString();
  }

  @EventHandler
  public static void onDisconnect(BotDisconnectedEvent event) {
    // Clean up executed connections set when bot disconnects
    EXECUTED_CONNECTIONS.remove(event.connection());
  }

  @EventHandler
  public void onSettingsRegistryInit(InstanceSettingsRegistryInitEvent event) {
    event.settingsRegistry().addPluginPage(AutoSpifftopiaCharacterSettings.class, "Auto Spifftopia Character", this, "user-plus", AutoSpifftopiaCharacterSettings.ENABLED);
  }

  @NoArgsConstructor(access = AccessLevel.NONE)
  private static class AutoSpifftopiaCharacterSettings implements SettingsObject {
    private static final String NAMESPACE = "auto-spifftopia-character";
    public static final BooleanProperty ENABLED =
      ImmutableBooleanProperty.builder()
        .namespace(NAMESPACE)
        .key("enabled")
        .uiName("Enable Auto Spifftopia Character")
        .description("Automatically create and select a character when bots connect to Spifftopia lobby")
        .defaultValue(false)
        .build();
    public static final MinMaxProperty INITIAL_DELAY =
      ImmutableMinMaxProperty.builder()
        .namespace(NAMESPACE)
        .key("initial-delay")
        .minValue(0)
        .maxValue(Integer.MAX_VALUE)
        .minEntry(ImmutableMinMaxPropertyEntry.builder()
          .uiName("Min initial delay (seconds)")
          .description("Minimum delay after connection before sending commands")
          .defaultValue(2)
          .build())
        .maxEntry(ImmutableMinMaxPropertyEntry.builder()
          .uiName("Max initial delay (seconds)")
          .description("Maximum delay after connection before sending commands")
          .defaultValue(4)
          .build())
        .build();
    public static final MinMaxProperty COMMAND_DELAY =
      ImmutableMinMaxProperty.builder()
        .namespace(NAMESPACE)
        .key("command-delay")
        .minValue(0)
        .maxValue(Integer.MAX_VALUE)
        .minEntry(ImmutableMinMaxPropertyEntry.builder()
          .uiName("Min command delay (seconds)")
          .description("Minimum delay between createcharacter and selectcharacter commands")
          .defaultValue(1)
          .build())
        .maxEntry(ImmutableMinMaxPropertyEntry.builder()
          .uiName("Max command delay (seconds)")
          .description("Maximum delay between createcharacter and selectcharacter commands")
          .defaultValue(3)
          .build())
        .build();
    public static final MinMaxProperty FIRST_NAME_LENGTH =
      ImmutableMinMaxProperty.builder()
        .namespace(NAMESPACE)
        .key("first-name-length")
        .minValue(3)
        .maxValue(16)
        .minEntry(ImmutableMinMaxPropertyEntry.builder()
          .uiName("Min first name length")
          .description("Minimum length for generated first names")
          .defaultValue(4)
          .build())
        .maxEntry(ImmutableMinMaxPropertyEntry.builder()
          .uiName("Max first name length")
          .description("Maximum length for generated first names")
          .defaultValue(8)
          .build())
        .build();
    public static final MinMaxProperty LAST_NAME_LENGTH =
      ImmutableMinMaxProperty.builder()
        .namespace(NAMESPACE)
        .key("last-name-length")
        .minValue(3)
        .maxValue(16)
        .minEntry(ImmutableMinMaxPropertyEntry.builder()
          .uiName("Min last name length")
          .description("Minimum length for generated last names")
          .defaultValue(4)
          .build())
        .maxEntry(ImmutableMinMaxPropertyEntry.builder()
          .uiName("Max last name length")
          .description("Maximum length for generated last names")
          .defaultValue(7)
          .build())
        .build();
  }
}

