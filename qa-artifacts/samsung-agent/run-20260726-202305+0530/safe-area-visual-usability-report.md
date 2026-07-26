# Samsung safe-area and visual usability pass

Device baseline:

- Samsung SM-M356B, 1080x2340
- Density override: 420
- Font scale: 1.15
- Status bar inset: `[0,0][1080,100]`
- Navigation bar inset: `[0,2214][1080,2340]`

## Safe-area findings

| Severity | Screen | Control type | Control / label | UI bounds | Samsung coordinates | Evidence | Finding |
| --- | --- | --- | --- | --- | --- | --- | --- |
| High | Google Play testing guide, initial bottom position | Primary bottom CTA | `Read Google’s official requirements` | `[239,2162][925,2214]` in XML, visually extends to/behind nav area | center approx `(582,2188)`; nav starts `y=2214` | `screenshots/016-google-play-guide-screen.png`, `uidumps/016-google-play-guide-screen.xml` | CTA is visually merged with the 3-button navigation area; lower rounded edge is clipped/hidden by nav bar. Taps near the lower half risk hitting system UI or doing nothing. |
| Medium | Google Play testing guide, after scroll | Header/back/action area | `Google Play testing guide`, back button | title `[201,79][889,158]`, back `[96,112][159,175]` | title begins at `y=79`, inside/overlapping status bar safe zone `0–100` | `screenshots/020-guide-button-scrolled-tap-result.png`, `uidumps/020-guide-button-scrolled-tap-result.xml` | Header content moves too high after scroll and visually merges with white status icons/time. Edge-to-edge top padding is insufficient in scrolled state. |
| Medium | Add app Step 1/2 | Primary bottom action | `Continue` | `[452,2062][628,2124]` | center approx `(540,2093)` | `screenshots/007-add-app-initial.png`, `screenshots/014-add-app-step2-invalid-group.png`, matching UI dumps | Without keyboard, button is technically above nav. With Samsung keyboard open, the bottom action and next fields are obscured; user must dismiss keyboard/scroll before continuing. This is especially noticeable on Step 2 after entering invalid group link. |
| Low | Main bottom navigation on Today/My apps/Discover/Profile | Bottom nav actions | `Today`, `My apps`, `Discover`, `Profile` | label bounds around `y=2127–2176`; nav inset starts `y=2214` | bottom labels end 38px above nav | `screenshots/003-after-finish-setup.png`, `004-my-apps-empty.png`, `005-discover-empty.png`, `006-profile.png` | Bottom nav is close to Samsung’s 3-button navigation bar but remains visually separated and tappable. No functional issue observed. |
| Low | Discover | Filter chip row | `Fewest slots` chip | visible text `[757,685][1003,747]`; chip extends off right edge | right edge near `x=1003` plus clipped chip border | `screenshots/005-discover-empty.png`, `uidumps/005-discover-empty.xml` | Horizontal chip row is somewhat clumped at font scale 1.15; clipping communicates scroll, but it looks crowded. |

## Visual usability findings

| Severity | Screen | Evidence | Finding |
| --- | --- | --- | --- |
| Medium | Google Play testing guide | `screenshots/016-google-play-guide-screen.png`, `020-guide-button-scrolled-tap-result.png` | Copy is dense and scroll-heavy at font scale 1.15. The primary CTA is not fully visible until the user reaches the bottom, and before scrolling it collides with the navigation bar. The primary next action is less obvious than expected for a help/guide page. |
| Medium | Add app Step 2 | `screenshots/014-add-app-step2-invalid-group.png`, `uidumps/014-add-app-step2-invalid-group.xml` | The instructional card plus validation copy consumes most of the viewport. When the keyboard is open, the user cannot see the remaining fields or Continue button. The validation error is clear, but the next action is obscured. |
| Low-Medium | Today first-run | `screenshots/003-after-finish-setup.png`, `uidumps/003-after-finish-setup.xml` | Primary and secondary actions are obvious, but the hero card is tall at font scale 1.15 and pushes supporting “How TestLoop works” content into the lower/partly-scroll area. Not a blocker. |
| Low | Discover empty | `screenshots/005-discover-empty.png`, `uidumps/005-discover-empty.xml` | Search/filter controls take substantial vertical space before the empty state. Primary `Add my app` is still obvious and safe. |
| Low | My apps empty | `screenshots/004-my-apps-empty.png`, `uidumps/004-my-apps-empty.xml` | Clean hierarchy, obvious primary and secondary actions. Large whitespace is acceptable for the empty state. |
| Low | Profile | `screenshots/006-profile.png`, `uidumps/006-profile.xml` | Settings rows are readable and separated. Destructive `Delete account` row was not reached in the first viewport; source shows it as static text, so visual/destructive affordance remains a concern but was not visible in captured Profile viewport. |

## Summary

The main Samsung safe-area risk is edge-to-edge handling on screens using bottom CTAs and scrollable content. The Google Play testing guide has the clearest problem: the bottom CTA is clipped/merged with Samsung’s navigation bar before scroll, and the header intrudes into the status bar after scroll. Add App’s bottom Continue action is safe without IME but becomes hidden by Samsung Keyboard during form entry.
