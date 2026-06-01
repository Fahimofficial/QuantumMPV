package com.quantummpv.app.ui.preferences

import com.quantummpv.app.ui.icons.Icon
import com.quantummpv.app.ui.icons.Icons

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.quantummpv.app.repository.wyzie.WyzieEncodings
import com.quantummpv.app.repository.wyzie.WyzieFormats
import com.quantummpv.app.repository.wyzie.WyzieSources
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quantummpv.app.R
import com.quantummpv.app.preferences.SubtitlesPreferences
import com.quantummpv.app.preferences.preference.collectAsState
import com.quantummpv.app.presentation.Screen
import com.quantummpv.app.ui.utils.LocalBackStack
import com.quantummpv.app.ui.utils.popSafely
import com.quantummpv.app.utils.media.copyFontsFromDirectory
import com.quantummpv.app.utils.media.loadCustomFontEntries
import com.quantummpv.app.utils.media.resolveSubtitleStorageDirectory
import com.github.k1rakishou.fsaf.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.ListPreference
import com.quantummpv.app.ui.preferences.components.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import android.net.Uri
import com.quantummpv.app.repository.subtitle.OnlineSubtitleSearchMode
import com.quantummpv.app.repository.subtitlehub.MpvRxSubtitleHubSources
import com.quantummpv.app.repository.wyzie.WyzieLanguages
import org.koin.compose.koinInject
import java.io.File

@Serializable
object SubtitlesPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val preferences = koinInject<SubtitlesPreferences>()
    val fileManager = koinInject<FileManager>()
    val scope = rememberCoroutineScope()
    var fontRefreshKey by remember { mutableStateOf(0) }

    fun reloadFontsFrom(uriString: String) {
      if (uriString.isBlank()) return
      scope.launch(Dispatchers.IO) {
        val copiedFonts = copyFontsFromDirectory(context, fileManager, uriString)
        withContext(Dispatchers.Main) {
          fontRefreshKey++
          Toast.makeText(
            context,
            if (copiedFonts > 0) {
              context.getString(R.string.fonts_loaded, copiedFonts)
            } else {
              context.getString(R.string.pref_subtitles_font_no_custom)
            },
            Toast.LENGTH_SHORT,
          ).show()
        }
      }
    }

    val fontFolderPicker =
      rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
      ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }.onFailure { error ->
          Toast.makeText(
            context,
            context.getString(R.string.pref_subtitles_font_copy_failed, error.message ?: "Unknown error"),
            Toast.LENGTH_SHORT,
          ).show()
        }

        val uriString = uri.toString()
        preferences.fontsFolder.set(uriString)
        reloadFontsFrom(uriString)
      }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_subtitles),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(
              onClick = { backstack.popSafely() },
            ) {
              Icon(
                Icons.Outlined.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val subtitleSaveFolder by preferences.subtitleSaveFolder.collectAsState()
        val fontsFolder by preferences.fontsFolder.collectAsState()
        val selectedFont by preferences.font.collectAsState()
        val wyzieHearingImpaired by preferences.wyzieHearingImpaired.collectAsState()
        val wyzieSources by preferences.wyzieSources.collectAsState()
        val wyzieFormats by preferences.wyzieFormats.collectAsState()
        val wyzieEncodings by preferences.wyzieEncodings.collectAsState()
        val wyzieApiKey by preferences.wyzieApiKey.collectAsState()
        val wyzieAiSubtitles by preferences.wyzieAiSubtitles.collectAsState()
        val onlineSubtitleSearchMode by preferences.onlineSubtitleSearchMode.collectAsState()
        val subtitleHubSources by preferences.subtitleHubSources.collectAsState()
        var customFonts by remember { mutableStateOf<List<String>>(emptyList()) }

        LaunchedEffect(fontsFolder, fontRefreshKey) {
          customFonts =
            loadCustomFontEntries(context)
              .map { it.familyName }
              .distinct()
        }

        LazyColumn(
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
        ) {
          // === GENERAL SECTION ===
          item {
            PreferenceSectionHeader(title = stringResource(R.string.general))
          }

          item {
            PreferenceCard {

              val preferredLanguages by preferences.preferredLanguages.collectAsState()
              TextFieldPreference(
                value = preferredLanguages,
                onValueChange = preferences.preferredLanguages::set,
                textToValue = { it },
                title = { Text(stringResource(R.string.pref_preferred_languages)) },
                summary = {
                  if (preferredLanguages.isNotBlank()) {
                    Text(
                      preferredLanguages,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  } else {
                    Text(
                      stringResource(R.string.not_set_video_default),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                },
                textField = { value, onValueChange, _ ->
                  Column {
                    Text(stringResource(R.string.enter_language_codes))
                    TextField(
                      value,
                      onValueChange,
                      modifier = Modifier.fillMaxWidth(),
                      placeholder = { Text(stringResource(R.string.language_codes_placeholder)) },
                    )
                  }
                },
              )
              
              PreferenceDivider()

              val autoload by preferences.autoloadMatchingSubtitles.collectAsState()
              SwitchPreference(
                value = autoload,
                onValueChange = { preferences.autoloadMatchingSubtitles.set(it) },
                title = { Text(stringResource(R.string.pref_subtitles_autoload_title)) },
                summary = {
                  Text(
                    stringResource(R.string.pref_subtitles_autoload_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              val overrideAss by preferences.overrideAssSubs.collectAsState()
              SwitchPreference(
                value = overrideAss,
                onValueChange = { preferences.overrideAssSubs.set(it) },
                title = { Text(stringResource(R.string.player_sheets_sub_override_ass)) },
                summary = {
                  Text(
                    stringResource(R.string.player_sheets_sub_override_ass_subtitle),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              val scaleByWindow by preferences.scaleByWindow.collectAsState()
              SwitchPreference(
                value = scaleByWindow,
                onValueChange = { preferences.scaleByWindow.set(it) },
                title = { Text(stringResource(R.string.player_sheets_sub_scale_by_window)) },
                summary = {
                  Text(
                    stringResource(R.string.player_sheets_sub_scale_by_window_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

            }
          }

          // === FONT SECTION ===
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_section_subtitle_fonts))
          }

          item {
            PreferenceCard {
              Preference(
                title = { Text(stringResource(R.string.pref_subtitles_fonts_dir)) },
                summary = {
                  val folderSummary =
                    if (fontsFolder.isBlank()) {
                      stringResource(R.string.pref_subtitles_font_directory_summary)
                    } else {
                      val displayPath =
                        runCatching { getSimplifiedPathFromUri(fontsFolder) }
                          .getOrElse { Uri.decode(fontsFolder) ?: fontsFolder }
                      stringResource(R.string.pref_subtitles_font_directory_selected, displayPath)
                    }
                  Text(
                    text = folderSummary,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                icon = {
                  Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { fontFolderPicker.launch(null) },
              )

              PreferenceDivider()

              val fontValues =
                remember(customFonts, selectedFont) {
                  (listOf("") + customFonts + listOf(selectedFont).filter { it.isNotBlank() }).distinct()
                }
              val fontValue = if (selectedFont in fontValues) selectedFont else ""
              val fontLabel =
                if (selectedFont.isBlank()) {
                  stringResource(R.string.pref_subtitles_font_default)
                } else {
                  selectedFont
                }

              ListPreference(
                value = fontValue,
                onValueChange = preferences.font::set,
                values = fontValues,
                valueToText = {
                  AnnotatedString(it.ifBlank { context.getString(R.string.pref_subtitles_font_default) })
                },
                title = { Text(stringResource(R.string.pref_subtitles_font_title)) },
                summary = {
                  Column {
                    Text(
                      stringResource(R.string.pref_subtitles_font_summary, fontLabel),
                      color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                      if (customFonts.isEmpty()) {
                        stringResource(R.string.pref_subtitles_font_no_custom)
                      } else {
                        stringResource(R.string.fonts_loaded, customFonts.size)
                      },
                      color = MaterialTheme.colorScheme.outline,
                      style = MaterialTheme.typography.bodySmall,
                    )
                  }
                },
              )

              PreferenceDivider()

              Preference(
                title = { Text(stringResource(R.string.reload_fonts)) },
                summary = {
                  Text(
                    stringResource(R.string.reload_fonts_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                enabled = fontsFolder.isNotBlank(),
                onClick = { reloadFontsFrom(fontsFolder) },
              )

              if (fontsFolder.isNotBlank() || selectedFont.isNotBlank() || customFonts.isNotEmpty()) {
                PreferenceDivider()

                Preference(
                  title = {
                    Text(
                      stringResource(R.string.clear_font_directory),
                      color = MaterialTheme.colorScheme.error,
                    )
                  },
                  icon = {
                    Icon(
                      Icons.Default.Clear,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.error,
                    )
                  },
                  onClick = {
                    preferences.fontsFolder.set("")
                    preferences.font.set("")
                    File(context.filesDir, "fonts").deleteRecursively()
                    fontRefreshKey++
                  },
                )
              }
            }
          }

          // === ONLINE SUBTITLE SECTION ===
          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_section_subtitle_search))
          }

          item {
            PreferenceCard {
              var showClearDialog by remember { mutableStateOf(false) }
              val scope = androidx.compose.runtime.rememberCoroutineScope()

              ListPreference(
                value = onlineSubtitleSearchMode,
                onValueChange = preferences.onlineSubtitleSearchMode::set,
                values = OnlineSubtitleSearchMode.values().toList(),
                valueToText = { AnnotatedString(it.displayName) },
                title = { Text(stringResource(R.string.pref_subtitles_search_mode_title)) },
                summary = {
                  Text(
                    onlineSubtitleSearchMode.displayName,
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              MultiChoicePreference(
                title = { Text(stringResource(R.string.pref_subtitles_subhub_sources_title)) },
                summary = {
                  val summaryText = if (subtitleHubSources.isEmpty() || subtitleHubSources.contains("all")) {
                    stringResource(R.string.pref_all_sources)
                  } else {
                    subtitleHubSources.mapNotNull { MpvRxSubtitleHubSources.ALL[it] }.joinToString(", ")
                  }
                  Text(summaryText, color = MaterialTheme.colorScheme.outline)
                },
                values = MpvRxSubtitleHubSources.ALL,
                selectedValues = subtitleHubSources,
                onValuesChange = { preferences.subtitleHubSources.set(it) },
                hasAllOption = true,
              )

              PreferenceDivider()

              TextFieldPreference(
                value = wyzieApiKey,
                onValueChange = preferences.wyzieApiKey::set,
                textToValue = { it.trim() },
                title = { Text(stringResource(R.string.pref_wyzie_api_key_title)) },
                summary = {
                  if (wyzieApiKey.isBlank()) {
                    Text(stringResource(R.string.pref_wyzie_api_key_summary_error), color = MaterialTheme.colorScheme.error)
                  } else {
                    Text(stringResource(R.string.pref_wyzie_api_key_summary_saved), color = MaterialTheme.colorScheme.outline)
                  }
                },
                textField = { value, onValueChange, _ ->
                  Column {
                    Text(stringResource(R.string.pref_wyzie_api_key_dialog_text))
                    TextField(
                      value = value,
                      onValueChange = onValueChange,
                      modifier = Modifier.fillMaxWidth(),
                      placeholder = { Text(stringResource(R.string.pref_wyzie_api_key_placeholder)) },
                      singleLine = true,
                    )
                  }
                },
              )

              PreferenceDivider()

              // Wyzie Sources
              MultiChoicePreference(
                title = { Text(stringResource(R.string.pref_subtitle_sources_title)) },
                summary = {
                  val summaryText = if (wyzieSources.isEmpty() || wyzieSources.contains("all")) {
                    stringResource(R.string.pref_all_sources)
                  } else {
                    wyzieSources.mapNotNull { WyzieSources.ALL[it] }.joinToString(", ")
                  }
                  Text(summaryText, color = MaterialTheme.colorScheme.outline)
                },
                values = WyzieSources.ALL,
                selectedValues = wyzieSources,
                onValuesChange = { preferences.wyzieSources.set(it) },
                hasAllOption = true
              )

              PreferenceDivider()

              // Languages
              val subtitleSearchLanguages by preferences.subtitleSearchLanguages.collectAsState()
              MultiChoicePreference(
                title = { Text(stringResource(R.string.pref_subtitles_search_languages)) },
                summary = {
                  val summaryText = if (subtitleSearchLanguages.isEmpty() || subtitleSearchLanguages.contains("all")) {
                    stringResource(R.string.all_languages)
                  } else {
                    subtitleSearchLanguages.mapNotNull { WyzieLanguages.ALL[it] }.joinToString(", ")
                  }
                  Text(summaryText, color = MaterialTheme.colorScheme.outline)
                },
                values = WyzieLanguages.SORTED,
                selectedValues = subtitleSearchLanguages,
                onValuesChange = { preferences.subtitleSearchLanguages.set(it) },
                hasAllOption = true
              )

              PreferenceDivider()

              // Advanced Filters (Toggleable)
              var showAdvanced by remember { mutableStateOf(false) }
              Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(16.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = stringResource(R.string.pref_section_advanced_search_filters),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                  )
                  Icon(
                    imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
                
                if (showAdvanced) {
                  Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SwitchPreference(
                      value = wyzieHearingImpaired,
                      onValueChange = { preferences.wyzieHearingImpaired.set(it) },
                      title = { Text(stringResource(R.string.pref_hearing_impaired_title)) },
                      summary = { Text(stringResource(R.string.pref_hearing_impaired_summary)) }
                    )

                    PreferenceDivider()

                    val aiOptions = mapOf(
                      "all" to stringResource(R.string.pref_ai_subtitles_all),
                      "human" to stringResource(R.string.pref_ai_subtitles_human_only),
                      "ai" to stringResource(R.string.pref_ai_subtitles_ai_only)
                    )
                    MultiChoicePreference(
                      title = { Text(stringResource(R.string.pref_ai_subtitles_title)) },
                      summary = {
                        val current = aiOptions[wyzieAiSubtitles] ?: stringResource(R.string.pref_ai_subtitles_all)
                        Text(current, color = MaterialTheme.colorScheme.outline)
                      },
                      values = aiOptions,
                      selectedValues = setOf(wyzieAiSubtitles),
                      onValuesChange = { 
                        if (it.isNotEmpty()) preferences.wyzieAiSubtitles.set(it.first()) 
                      },
                      hasAllOption = false
                    )

                    PreferenceDivider()

                    MultiChoicePreference(
                      title = { Text(stringResource(R.string.pref_preferred_formats_title)) },
                      summary = {
                        val summaryText = if (wyzieFormats.isEmpty() || wyzieFormats.contains("all")) {
                          stringResource(R.string.pref_all_sources)
                        } else {
                          wyzieFormats.mapNotNull { WyzieFormats.ALL[it] }.joinToString(", ")
                        }
                        Text(summaryText, color = MaterialTheme.colorScheme.outline)
                      },
                      values = WyzieFormats.ALL,
                      selectedValues = wyzieFormats,
                      onValuesChange = { preferences.wyzieFormats.set(it) },
                      hasAllOption = true
                    )

                    PreferenceDivider()

                    MultiChoicePreference(
                      title = { Text(stringResource(R.string.pref_preferred_encodings_title)) },
                      summary = {
                        val summaryText = if (wyzieEncodings.isEmpty() || wyzieEncodings.contains("all")) {
                          stringResource(R.string.pref_all_sources)
                        } else {
                          wyzieEncodings.mapNotNull { WyzieEncodings.ALL[it] }.joinToString(", ")
                        }
                        Text(summaryText, color = MaterialTheme.colorScheme.outline)
                      },
                      values = WyzieEncodings.ALL,
                      selectedValues = wyzieEncodings,
                      onValuesChange = { preferences.wyzieEncodings.set(it) },
                      hasAllOption = true
                    )
                    
                    Spacer(modifier = Modifier.size(16.dp))
                  }
                }
              }

              PreferenceDivider()

              Preference(
                title = { Text(stringResource(R.string.pref_subtitles_clear_downloads), color = MaterialTheme.colorScheme.error) },
                summary = { Text(stringResource(R.string.pref_subtitles_clear_downloads_summary)) },
                onClick = { showClearDialog = true },
                enabled = subtitleSaveFolder.isNotBlank()
              )

              if (showClearDialog) {
                AlertDialog(
                  onDismissRequest = { showClearDialog = false },
                  title = { Text(stringResource(R.string.pref_subtitles_clear_downloads)) },
                  text = { Text(stringResource(R.string.pref_subtitles_clear_downloads_confirmation)) },
                  confirmButton = {
                    TextButton(
                      onClick = {
                        showClearDialog = false
                        scope.launch(Dispatchers.IO) {
                          runCatching {
                            val uri = Uri.parse(subtitleSaveFolder)
                            val folder = resolveSubtitleStorageDirectory(context, uri.toString())
                            folder?.listFiles()?.forEach { it.delete() }
                            withContext(Dispatchers.Main) {
                              android.widget.Toast.makeText(context, R.string.toast_subtitles_cleared, android.widget.Toast.LENGTH_SHORT).show()
                            }
                          }.onFailure { e ->
                            withContext(Dispatchers.Main) {
                              android.widget.Toast.makeText(context, context.getString(R.string.pref_subtitle_search_error, e.message ?: "Unknown error"), android.widget.Toast.LENGTH_SHORT).show()
                            }
                          }
                        }
                      }
                    ) {
                      Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                  },
                  dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                      Text(stringResource(android.R.string.cancel))
                    }
                  }
                )
              }

              PreferenceDivider()
              
              // Wyzie Tag
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = stringResource(R.string.pref_subtitle_search_attribution),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = stringResource(R.string.pref_subtitle_search_link),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sub.wyzie.io"))
                    context.startActivity(intent)
                  }
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun MultiChoicePreference(
  title: @Composable () -> Unit,
  summary: @Composable () -> Unit,
  values: Map<String, String>,
  selectedValues: Set<String>,
  onValuesChange: (Set<String>) -> Unit,
  hasAllOption: Boolean = false
) {
  var showDialog by remember { mutableStateOf(false) }

  Preference(
    title = title,
    summary = summary,
    onClick = { showDialog = true }
  )

  if (showDialog) {
    AlertDialog(
      onDismissRequest = { showDialog = false },
      title = title,
      text = {
        val valuesList = values.toList()
        LazyColumn {
          items(count = valuesList.size, key = { index -> valuesList[index].first }) { index ->
            val entry = valuesList[index]
            val key = entry.first
            val checked = if (hasAllOption && (selectedValues.isEmpty() || selectedValues.contains("all"))) {
              key == "all"
            } else {
              selectedValues.contains(key)
            }
            
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  val newSet = selectedValues.toMutableSet()
                  if (hasAllOption) {
                    if (key == "all") {
                      newSet.clear()
                      newSet.add("all")
                    } else {
                      newSet.remove("all")
                      if (checked) newSet.remove(key) else newSet.add(key)
                      if (newSet.isEmpty()) newSet.add("all")
                    }
                  } else {
                    if (checked) newSet.remove(key) else newSet.add(key)
                  }
                  onValuesChange(newSet)
                }
                .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = checked,
                onCheckedChange = null
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = entry.second)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showDialog = false }) {
          Text(stringResource(android.R.string.ok))
        }
      }
    )
  }
}
