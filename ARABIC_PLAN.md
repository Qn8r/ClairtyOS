# Arabic Localization Complete Plan

## 1. Missing l10n_ Translations in values-ar/strings.xml

The following strings (lines 452-574 in Arabic strings.xml) are in English and need Arabic translation:

```
l10n_active
l10n_add_a_comment
l10n_add_plan
l10n_add_template_from_community
l10n_advanced_template
l10n_alarm_sound
l10n_analyze_create_template
l10n_analyzing_json
l10n_apply_now
l10n_avg_heart_rate
l10n_awesome
l10n_background_color
l10n_backup_name
l10n_backup_payload
l10n_calories
l10n_color
l10n_coming_soon
l10n_comments
l10n_copy_for_ai
l10n_default_package_will_be_enabled_automatica
l10n_delete
l10n_delete_all
l10n_delete_template
l10n_distance_m
l10n_download_starter_json
l10n_enable_custom_mode_in_settings_to_edit_cus
l10n_enjoy_update_1_04
l10n_equip_default_package
l10n_errors
l10n_example_generate_120_daily_quests_40_main_
l10n_experimental
l10n_finish
l10n_generate_prompt
l10n_go_to_settings
l10n_health_snapshot
l10n_hex_rrggbb
l10n_image
l10n_image_backgrounds_can_reduce_performance_o
l10n_image_transparency
l10n_import
l10n_import_encrypted_backup
l10n_includes_advanced_options_background_if_pr
l10n_items
l10n_level_up
l10n_log
l10n_manual_health_update
l10n_manual_update
l10n_message
l10n_next
l10n_no_achievements_unlocked_yet
l10n_no_active_main_quests_go_to_quests_templat
l10n_no_later
l10n_none
l10n_note
l10n_open_quests_templates
l10n_options
l10n_paste_ai_generated_json_here
l10n_paste_json
l10n_paste_your_encrypted_backup_import_applies
l10n_performance_note
l10n_plan
l10n_post
l10n_post_comment
l10n_prev
l10n_pro
l10n_prompt_ready_tap_copy_for_ai_and_paste_it_
l10n_prompt_tip_ask_ai_to_return_a_downloadable
l10n_quest_card_title
l10n_quest_title
l10n_quick_info
l10n_remove_quest
l10n_reset
l10n_reset_everything
l10n_reset_progress
l10n_save_apply
l10n_save_current_setup_to_template_before_rese
l10n_save_template
l10n_send
l10n_sign_in_required
l10n_sign_in_with_google
l10n_sign_out
l10n_skip
l10n_snooze
l10n_start_day
l10n_start_new_day
l10n_steps
l10n_stop_claim
l10n_swipe_left_on_home_to_open_drawer
l10n_swipe_right_on_home_to_go_main_then_journa
l10n_sync_health
l10n_tap_or_swipe_to_change_presets
l10n_tap_swatch_to_pick
l10n_tap_to_add_a_plan_for_this_day
l10n_tell_ai_your_quest_goal
l10n_template_flagged_for_safety_review_applyin
l10n_template_saved_are_you_sure_you_want_to_ap
l10n_template_was_auto_cleaned_for_safe_import
l10n_text
l10n_this_forces_a_new_day_calculation
l10n_this_removes_the_quest_from_today_you_can_
l10n_this_will_add_back_any_missing_default_que
l10n_this_will_change_theme_background_advanced
l10n_this_will_erase_progress_and_active_data
l10n_this_will_remove_earned_xp_but_keep_earned
l10n_tip_hold_item_icon_to_edit_or_delete
l10n_tips
l10n_title
l10n_transparency
l10n_txt_16_xp
l10n_type
l10n_uncheck
l10n_uncheck_quest
l10n_upload_json_file
l10n_vibration
l10n_warnings
l10n_we_need_more_data_to_draw_this_chart_check
l10n_xp_reward
l10n_yes_equip_now
l10n_you_are_about_to_save
l10n_you_need_to_sign_in_with_google_to_use_thi
l10n_your_gold
```

---

## 2. New String Keys Required

### A. Plan Types (6 keys)

| Key | English |
|-----|---------|
| plan_type_general | General |
| plan_type_workout | Workout |
| plan_type_deep_work | Deep Work |
| plan_type_hydrate | Hydrate |
| plan_type_sleep | Sleep |
| plan_type_study | Study |

### B. Feedback Categories (5 keys)

| Key | English |
|-----|---------|
| feedback_bug | Bug |
| feedback_uiux | UI/UX |
| feedback_feature_request | Feature Request |
| feedback_performance | Performance |
| feedback_general | General |

### C. Tiers (5 keys)

| Key | English |
|-----|---------|
| tier_starter | Starter |
| tier_consistency | Consistency |
| tier_progress | Progress |
| tier_challenge | Challenge |
| tier_mastery | Mastery |

### D. Days (9 keys)

| Key | English |
|-----|---------|
| day_today | Today |
| day_yesterday | Yesterday |
| day_monday | Monday |
| day_tuesday | Tuesday |
| day_wednesday | Wednesday |
| day_thursday | Thursday |
| day_friday | Friday |
| day_saturday | Saturday |
| day_sunday | Sunday |

### E. Snackbar Messages (62 keys)

| Key | English |
|-----|---------|
| snackbar_comment_failed | Comment failed to post. |
| snackbar_vote_failed | Vote failed. |
| snackbar_google_drive_first | Connect Google Drive first. |
| snackbar_cloud_sync_empty | Cloud sync failed: backup empty. |
| snackbar_cloud_backup_synced | Cloud backup synced. |
| snackbar_cloud_sync_failed | Cloud sync failed. Check Google account/permission. |
| snackbar_no_cloud_backup | No cloud backup found for this account. |
| snackbar_cloud_restored | Cloud backup restored. |
| snackbar_cloud_restore_failed | Cloud backup restore failed. |
| snackbar_google_cloud_connected | Google cloud connected. |
| snackbar_feedback_sent | Feedback sent. |
| snackbar_achievement_unlocked | New Achievement Unlocked! |
| snackbar_google_signin_canceled | Google sign-in canceled. |
| snackbar_google_id_token_failed | Failed to get Google ID token. |
| snackbar_google_signin_failed | Google sign-in failed. |
| snackbar_google_web_client_missing | Google Web Client ID not configured. |
| snackbar_signed_out | Signed out. |
| snackbar_data_schema_newer | Data schema is newer than this app build. |
| snackbar_community_offline | Community sync offline. Using local data. |
| snackbar_template_link_too_large | Template link too large. |
| snackbar_template_link_invalid | Template link is invalid or too large. |
| snackbar_read_template_failed | Failed to read template link. |
| snackbar_new_day_started | Start of a new day! |
| snackbar_publishing_cooldown | Publishing cooldown active. Try again in a few minutes. |
| snackbar_publishing_locked | Publishing temporarily locked due to spam protection. |
| snackbar_title_too_short | Title is too short. |
| snackbar_description_too_short | Description is too short. |
| snackbar_tags_limit | Use up to 8 tags. |
| snackbar_tags_invalid | Tags contain unsupported characters. |
| snackbar_unsafe_content | Publish blocked: unsafe content detected. |
| snackbar_title_duplicate | Challenge title already exists in community. |
| snackbar_nothing_safe_to_publish | Nothing safe to publish in this template. |
| snackbar_challenge_published | Challenge published! |
| snackbar_report_submitted | Report submitted. |
| snackbar_premium_template | This is a premium template. Enable Creator Pass (beta) in Settings. |
| snackbar_quest_removed | Quest removed from today. |
| snackbar_not_enough_gold | Not enough Gold |
| snackbar_custom_mode_required | Enable Custom Mode in Settings. |
| snackbar_shop_item_saved | Shop item saved |
| snackbar_plan_title_required | Plan title required. |
| snackbar_plan_added | Plan added |
| snackbar_plan_removed | Plan removed |
| snackbar_quest_pool_updated | Quest pool updated. Tap 'Start New Day' to apply. |
| snackbar_deleted | Deleted |
| snackbar_reset_complete | Reset complete. Default pack enabled. |
| snackbar_theme_applied | Theme & Quests Applied! |
| snackbar_template_saved | Template saved. |
| snackbar_daily_quest_saved | Daily quest saved. |
| snackbar_daily_quest_deleted | Daily quest deleted. |
| snackbar_main_quest_saved | Main quest saved. |
| snackbar_main_quest_deleted | Main quest deleted. |
| snackbar_incompatible_template | Template is incompatible. Re-export it from latest app version. |
| snackbar_family_quests_deleted | All "$family" quests deleted. |
| snackbar_backup_export_failed | Backup export failed. |
| snackbar_advanced_template_applied | Advanced template applied. |

### F. Onboarding Templates (3 keys)

| Key | English |
|-----|---------|
| template_real_daily_life | Real Daily Life System |
| template_limit_breaker | Limit Breaker (Saitama) |
| template_empty_start | Empty Start |

---

## 3. Kotlin Hardcoded Replacements by File

### A. UI.kt

**Location 1 - Lines 2204-2209** (Plan types)
```kotlin
// BEFORE:
var selectedPlanType by rememberSaveable { mutableStateOf("General") }
val planTypes = remember { listOf("General", "Workout", "Deep Work", "Hydrate", "Sleep", "Study") }

// AFTER:
var selectedPlanType by rememberSaveable { mutableStateOf(stringResource(R.string.plan_type_general)) }
val planTypes = remember { listOf(
    stringResource(R.string.plan_type_general),
    stringResource(R.string.plan_type_workout),
    stringResource(R.string.plan_type_deep_work),
    stringResource(R.string.plan_type_hydrate),
    stringResource(R.string.plan_type_sleep),
    stringResource(R.string.plan_type_study)
) }
```

**Location 2 - Line 2170** (AttributeRow)
```kotlin
// BEFORE:
AttributeRow("Strength", attributes.str)
AttributeRow("Intellect", attributes.int)
AttributeRow("Vitality", attributes.vit)
AttributeRow("Endurance", attributes.end)
AttributeRow("Faith", attributes.fth)

// AFTER:
AttributeRow(stringResource(R.string.attribute_strength), attributes.str)
AttributeRow(stringResource(R.string.attribute_intellect), attributes.int)
AttributeRow(stringResource(R.string.attribute_vitality), attributes.vit)
AttributeRow(stringResource(R.string.attribute_endurance), attributes.end)
AttributeRow(stringResource(R.string.attribute_faith), attributes.fth)
```

**Location 3 - Lines 2774, 2781** (Feedback categories)
```kotlin
// BEFORE:
var feedbackCategory by rememberSaveable { mutableStateOf("General") }
val feedbackCategories = remember { listOf("Bug", "UI/UX", "Feature Request", "Performance", "General") }

// AFTER:
var feedbackCategory by rememberSaveable { mutableStateOf(stringResource(R.string.feedback_general)) }
val feedbackCategories = remember { listOf(
    stringResource(R.string.feedback_bug),
    stringResource(R.string.feedback_uiux),
    stringResource(R.string.feedback_feature_request),
    stringResource(R.string.feedback_performance),
    stringResource(R.string.feedback_general)
) }
```

### B. Logic.kt

**Location 1 - Lines 107-108** (Today/Yesterday)
```kotlin
// BEFORE:
today -> if (isAr) "اليوم" else "Today"
today - 1L -> if (isAr) "أمس" else "Yesterday"

// AFTER: Replace with stringResource calls (requires passing context)
```

**Location 2 - Lines 616-622** (Weekdays)
```kotlin
// BEFORE:
Calendar.MONDAY -> if (isAr) "الإثنين" else "Monday"
Calendar.TUESDAY -> if (isAr) "الثلاثاء" else "Tuesday"
Calendar.WEDNESDAY -> if (isAr) "الأربعاء" else "Wednesday"
Calendar.THURSDAY -> if (isAr) "الخميس" else "Thursday"
Calendar.FRIDAY -> if (isAr) "الجمعة" else "Friday"
Calendar.SATURDAY -> if (isAr) "السبت" else "Saturday"
else -> if (isAr) "الأحد" else "Sunday"

// AFTER: Replace with stringResource(R.string.day_monday), etc.
```

**Location 3 - Line 669** (Tiers)
```kotlin
// BEFORE:
val tierLabels = if (isAr) listOf("مبتدئ", "استمرارية", "تقدم", "تحدي", "إتقان") else listOf("Starter", "Consistency", "Progress", "Challenge", "Mastery")

// AFTER:
val tierLabels = listOf(
    stringResource(R.string.tier_starter),
    stringResource(R.string.tier_consistency),
    stringResource(R.string.tier_progress),
    stringResource(R.string.tier_challenge),
    stringResource(R.string.tier_mastery)
)
```

**Location 4 - Lines 364-740** (Quest templates - 50+ strings)
- Requires significant refactoring to move quest names to string resources
- Each quest template name needs a string resource key

**Location 5 - Lines 427-430, 514, 631, 644** (Onboarding templates)
```kotlin
// BEFORE:
templateName = if (isAr) "كاسر الحدود (سايتاما)" else "Limit Breaker (Saitama)"
templateName = if (lang == "ar") "نظام الحياة اليومية الحقيقي" else "Real Daily Life System"
templateName = if (lang == "ar") "بداية فارغة" else "Empty Start"

// AFTER:
templateName = stringResource(R.string.template_limit_breaker)
templateName = stringResource(R.string.template_real_daily_life)
templateName = stringResource(R.string.template_empty_start)
```

### C. MainActivity.kt

**All snackbar messages** (~62 locations)
```kotlin
// BEFORE:
snackbarHostState.showSnackbar("Comment failed to post.")

// AFTER:
snackbarHostState.showSnackbar(stringResource(R.string.snackbar_comment_failed))
```

---

## 4. RTL Steps

### Step 1: Add RTL configuration to values-ar/strings.xml

Add at the top of the file (after opening `<resources>`):

```xml
<!-- RTL Configuration -->
<string name="layout_direction">rtl</string>
```

### Step 2: Create values-ar folder structure (if missing)

Ensure these files exist:
- `values-ar/strings.xml` (exists - needs updates)
- `values-night/strings.xml` (check if needs Arabic variant)

### Step 3: AndroidManifest.xml verification

Ensure the application tag has:
```xml
android:supportsRtl="true"
```

### Step 4: Compose RTL support

The app uses Compose which handles RTL automatically via:
- `CompositionLocalProvider` with `LocalLayoutDirection`
- No explicit layoutDirection modifiers needed for most components

### Step 5: Test checklist

- [ ] All text is mirrored correctly (start/end)
- [ ] Icons that indicate direction are flipped
- [ ] Navigation drawer opens from right
- [ ] Scroll direction is correct
- [ ] Numbers display correctly (Arabic-Indic vs Western)
- [ ] Dates/times format correctly for Arabic locale

---

## 5. Implementation Order

1. **Phase 1**: Add all new string keys to `values/strings.xml` (English)
2. **Phase 2**: Add complete Arabic translations to `values-ar/strings.xml` including:
   - All new keys
   - RTL configuration
   - Fix all missing l10n_ translations
3. **Phase 3**: Refactor `UI.kt` - replace hardcoded strings
4. **Phase 4**: Refactor `Logic.kt` - replace isAr inline strings
5. **Phase 5**: Refactor `MainActivity.kt` - replace snackbar strings
6. **Phase 6**: Verify values-ar has zero English strings
7. **Phase 7**: Test RTL layout

---

## 6. Notes

- Quest templates in Logic.kt (50+ strings) require careful handling - consider creating a separate `QuestStrings.kt` object for localization
- Snackbar messages in MainActivity.kt need to import `stringResource` which requires Composable context
- Some snackbar calls are inside `scope.launch {}` - may need `rememberCoroutineScope()` or pass string resource as parameter
- The existing `l10n_` strings in values-ar should be replaced with proper named keys during this migration
