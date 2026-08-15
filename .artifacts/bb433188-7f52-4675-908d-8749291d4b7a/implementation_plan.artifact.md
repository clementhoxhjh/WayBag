# Implementation Plan - Apple-Inspired Glassmorphism UI

Transform the WayBag app into a futuristic, clean, and tactile "Glassmorphism" experience inspired by Apple's aesthetic.

## Proposed Changes

---

### Core Theme & Styling

#### [MODIFY] [Color.kt](file:///C:/Users/cleme/AndroidStudioProjects/WayBag/app/src/main/java/com/example/waybag/ui/theme/Color.kt)
- Add `Lavender_White` (#F2F2F7), `Glass_Primary` (Light Green with gradient intent), `Deep_Gray` (#333333), and various glass-alpha colors.

#### [MODIFY] [Theme.kt](file:///C:/Users/cleme/AndroidStudioProjects/WayBag/app/src/main/java/com/example/waybag/ui/theme/Theme.kt)
- Update the default theme to reflect the light, airy lavender aesthetic.

---

### Glassmorphism Components

#### [NEW] [GlassBackground.kt](file:///C:/Users/cleme/AndroidStudioProjects/WayBag/app/src/main/java/com/example/waybag/ui/components/GlassBackground.kt)
- A component that draws the diffused lavender gradient with the blurred rainbow flare in the corner.

#### [NEW] [GlassPanel.kt](file:///C:/Users/cleme/AndroidStudioProjects/WayBag/app/src/main/java/com/example/waybag/ui/components/GlassPanel.kt)
- A reusable container with `Modifier.blur`, a white inner shadow (border), and a fine gray hairline.

#### [MODIFY] [WayBagLogo.kt](file:///C:/Users/cleme/AndroidStudioProjects/WayBag/app/src/main/java/com/example/waybag/ui/components/WayBagLogo.kt)
- Redesign as a frosted glass disc with a single-line art tote bag and a soft green glow effect.

#### [MODIFY] [WayBagButton.kt](file:///C:/Users/cleme/AndroidStudioProjects/WayBag/app/src/main/java/com/example/waybag/ui/components/WayBagButton.kt)
- Redesign as soft-edge, light green pill shapes with fine gradients and crisp white text.

#### [MODIFY] [WayBagTextField.kt](file:///C:/Users/cleme/AndroidStudioProjects/WayBag/app/src/main/java/com/example/waybag/ui/components/WayBagTextField.kt)
- Update to use the `GlassPanel` style with dark gray text.

---

### Screens Refinement

#### [MODIFY] [WelcomeScreen.kt](file:///C:/Users/cleme/AndroidStudioProjects/WayBag/app/src/main/java/com/example/waybag/ui/screens/WelcomeScreen.kt)
- Apply the glass background and the new logo style.

#### [MODIFY] [LoginScreen.kt](file:///C:/Users/cleme/AndroidStudioProjects/WayBag/app/src/main/java/com/example/waybag/ui/screens/LoginScreen.kt)
- Implement frosted glass panels for the input fields.

#### [MODIFY] [ForgotPasswordScreen.kt](file:///C:/Users/cleme/AndroidStudioProjects/WayBag/app/src/main/java/com/example/waybag/ui/screens/ForgotPasswordScreen.kt)
- Center the input and button inside a single floating frosted glass module.

#### [MODIFY] [SignUpScreen.kt](file:///C:/Users/cleme/AndroidStudioProjects/WayBag/app/src/main/java/com/example/waybag/ui/screens/SignUpScreen.kt)
- Use a refined avatar circle and vertical stack of glass input panels.

## Verification Plan

### Manual Verification
- Visual inspection of background gradients and rainbow flare.
- Verify blur intensity and text legibility on all glass panels.
- Ensure transitions between screens feel fluid and maintain the glass theme.
