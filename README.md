# 📌 AR File Tagger

**AR File Tagger** is an Augmented Reality application that lets users **capture images**, attach **textual tags** (filename, file path, deadline), and later **view these tags floating in AR** when scanning the same image again. All captured images and tags are **persisted locally and in Firebase Firestore**, allowing consistent AR recognition across sessions.

---

## ✨ Features

* **📸 Image Capture** – Capture 2D images (photos, documents, object surfaces).
* **🏷️ AR Tagging** – Add custom textual tags (`filename`, `filepath`, `deadline`).
* **💾 Persistent Data** – Save images locally and tags to **Firestore**.
* **🕶️ Real-Time AR Display** – Tags appear anchored to the detected physical image.
* **🗂️ Tag Management** – View, edit, or delete saved AR tags.
* **📏 Optimized Tag Placement** – Tags render flat on the detected image for readability.

---

## 🛠️ Technologies Used

### **Platform**

* Android

### **Language**

* Kotlin

### **Augmented Reality**

* Google ARCore
* Google Sceneform

### **Backend**

* Firebase Firestore
* Firebase Authentication

### **UI Toolkit**

* XML-based Android Views

### **Libraries**

* AndroidX Components
* Google Material Components
* RecyclerView & CardView

---

## 🚀 Setup Instructions

### **1. Clone the Repository**

```bash
git clone https://github.com/Arshad4786/AR-File-Tagger
cd ar-file-tagger
```

---

## **2. Firebase Setup**

### **Create Firebase Project**

1. Go to **Firebase Console** → create new project.
2. Add an **Android app**.
3. Download the generated **google-services.json**.
4. Place it in:

```
app/google-services.json
```

### **Enable Firestore**

1. Firebase Console → **Build → Firestore Database**
2. Click **Create Database** → choose Production/Test mode.

#### **Set Firestore Rules**

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /imageTags/{tagId} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### **Enable Anonymous Authentication**

Firebase Console → **Authentication → Sign-in method → Anonymous → Enable**

---

## **3. Android Studio Setup**

Open the project in Android Studio.

### Add Dependencies (`app/build.gradle.kts`)

```kotlin
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    
    // Sceneform (example)
    // implementation("com.google.ar.sceneform:core:1.17.1")
    // implementation("com.google.ar.sceneform.ux:sceneform-ux:1.17.1")

    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
}
```

### Sync, Clean & Rebuild

* **File → Sync Project with Gradle Files**
* **Build → Clean Project**
* **Build → Rebuild Project**

---

## 📱 How to Use the App

### **1. Launch the App**

The app automatically signs in anonymously and shows:

> Authenticated and ready!

### **2. Capture an Image (Create a Tag)**

1. Point your device at any flat, clear image.
2. Tap the **camera floating action button**.
3. Capture the photo → return to the app.
4. Enter tag details:

   * **Filename** (e.g., MyReport.pdf)
   * **Filepath** (/Documents/ProjectX/)
   * **Deadline** (End of Q2)
5. Tap **Save**.

### **3. View AR Tags (Image Detection)**

* Scan the same physical image.
* ARCore recognizes it and displays the tag floating above the surface.
* Tags stay anchored even when you move around.

### **4. Manage Saved Tags**

Tap the **edit button (left FAB)** to open the Tag List screen.

#### **Edit Tag**

* Tap **Edit**
* Modify fields
* Tap **Save**

#### **Delete Tag**

* Tap **Delete**
* Confirm deletion

### **5. Persistence**

* Close the app and reopen it.
* Scan a previously tagged image.
* Data loads from internal storage + Firestore.

---

## 💡 Future Enhancements

* Manual fine-tuning for tag placement
* Multiple tags per image
* Media tagging (audio, video, URLs)
* Google Sign-In / user-level tag management
* Tag sharing
* Image preprocessing for better AR tracking
