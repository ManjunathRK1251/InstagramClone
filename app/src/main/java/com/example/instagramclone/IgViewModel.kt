package com.example.instagramclone

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.instagramclone.data.Event
import com.example.instagramclone.data.PostData
import com.example.instagramclone.data.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.*
import javax.inject.Inject

const val USERS = "users"
const val POSTS = "posts"

@HiltViewModel
class IgViewModel @Inject constructor(
    val auth: FirebaseAuth,
    val db: FirebaseFirestore,
    val storage: FirebaseStorage
) : ViewModel() {

    val signedIn = mutableStateOf(false)
    val inProgress = mutableStateOf(false)
    val userData = mutableStateOf<UserData?>(null)
    val popupNotification = mutableStateOf<Event<String>?>(null)

    //When the system starts up and instantiate our view model, init block will check whether we have a user or not
    init {
        auth.signOut()
        //firebase authentication remembers if the user has signed in or not, which can be accessed by 'currentUser' attribute
        val currentUser = auth.currentUser
        signedIn.value = currentUser != null

        currentUser?.uid?.let { uid ->
            getUserData(uid)
        }
    }

    fun onSignup(username: String, email: String, pass: String) {
        if(username.isEmpty() or email.isEmpty() or pass.isEmpty())
        {
            handleException(customMessage = "Please fill in all the fields")
            return
        }
        inProgress.value = true

        //In firestore we'll be checking whether a user already exists
        db.collection(USERS).whereEqualTo("username", username).get()
            .addOnSuccessListener {  documents ->
                if(documents.size() > 0){
                    handleException(customMessage = "Username already exists")
                    inProgress.value = false
                }
                else{
                    //create a new user
                    auth.createUserWithEmailAndPassword(email, pass)
                        .addOnCompleteListener{ task ->
                            if(task.isSuccessful){
                                signedIn.value = true
                                //create profile
                                createOrUpdateProfile(username = username)
                            }
                            else{
                                handleException(exception = task.exception, customMessage = "Signup failed")
                            }
                            inProgress.value = false
                        }
                }
            }
            .addOnFailureListener {  }
    }


    fun onLogin(email: String, pass: String){
        if(email.isEmpty() or pass.isEmpty())
        {
            handleException(customMessage = "Please fill in all the fields")
            return
        }

        inProgress.value = true
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener{ task ->
                if(task.isSuccessful){
                    signedIn.value = true
                    inProgress.value = false

                    auth.currentUser?.uid?.let { uid ->
                        handleException(customMessage = "Login success")
                        getUserData(uid)
                    }
                }
                else{
                    handleException(task.exception, customMessage =  "Login Failed")
                    inProgress.value = false
                }

            }
            .addOnFailureListener { exc ->
                handleException(exc, customMessage = "Login Failed")
                inProgress.value = false
            }
    }

    private fun createOrUpdateProfile(
        name: String? = null,
        username: String? = null,
        bio: String? = null,
        imageUrl: String? = null
        ){

            val uid = auth.currentUser?.uid
            val userData = UserData(
                userId = uid,
                name = name ?: userData.value?.name,
                userName = username ?: userData.value?.userName,
                bio = bio ?: userData.value?.bio,
                imageUrl = imageUrl ?: userData.value?.bio,
                following = userData.value?.following
            )

            uid?.let { uid ->
                inProgress.value = true
                db.collection(USERS).document(uid).get().addOnSuccessListener {

                    //if the user profile already exists, then we should just update the old info with the new data
                    if(it.exists())
                    {
                        it.reference.update(userData.toMap())
                            .addOnSuccessListener {
                                this.userData.value = userData
                                inProgress.value = false
                            }
                            .addOnFailureListener {
                                handleException(it, customMessage = "Cannot update user")
                                inProgress.value = false
                            }
                    }
                    else{
                        //user doesn't exist, so create a new user profile
                        db.collection(USERS).document(uid).set(userData)
                        //after completing the creation of the user, we should retrieve the user data
                        getUserData(uid)
                        inProgress.value = false
                    }
                }
                    .addOnFailureListener { exc ->
                        handleException(exception = exc, customMessage = "Cannot create user")
                        inProgress.value = false
                    }
            }
    }

    private fun getUserData(uid: String){
        inProgress.value = true
        db.collection(USERS).document(uid).get()
            .addOnSuccessListener {
                val user = it.toObject<UserData>()

                userData.value = user
                inProgress.value = false
                //popupNotification.value = Event("User data retrieved successfully")
            }
            .addOnFailureListener { exc ->
                handleException(exception = exc, customMessage = "Cannot retrieve user data")
                inProgress.value = false
            }
    }

    fun handleException(exception: Exception? = null, customMessage: String = ""){
        exception?.printStackTrace()
        val errorMessage = exception?.localizedMessage ?: ""
        val message = if (customMessage.isEmpty()) errorMessage else "$customMessage : $errorMessage"
        popupNotification.value = Event(message)
    }

    fun updateProfileData(name: String, username: String, bio: String){
        createOrUpdateProfile(name, username, bio)
    }

    private fun uploadImage(uri: Uri, onSuccess: (Uri) -> Unit){
        inProgress.value = true

        val storageRef = storage.reference
        val uuid = UUID.randomUUID()
        val imageRef = storageRef.child("images/$uuid")
        val uploadTask = imageRef.putFile(uri)

        uploadTask.addOnSuccessListener {
            //if the task is successful we'll download the image from the URL
            val result = it.metadata?.reference?.downloadUrl
            result?.addOnSuccessListener(onSuccess)
        }
            .addOnFailureListener{ exc ->
                handleException(exc)
                inProgress.value = false
            }
    }

    fun uploadProfileImage(uri: Uri){
        uploadImage(uri){
            createOrUpdateProfile(imageUrl = it.toString())
        }
    }

    fun onLogout() {
        auth.signOut()
        signedIn.value = false
        userData.value = null
        popupNotification.value = Event("Logged out")
    }

    fun onNewPost(uri:Uri, description: String, onPostSuccess: () -> Unit){
        uploadImage(uri){
            onCreatePost(uri, description, onPostSuccess)
        }
    }

    private fun onCreatePost(imageUri: Uri, description: String, onPostSuccess: () -> Unit ){
        inProgress.value = true
        val currentUid = auth.currentUser?.uid
        val currentUsername = userData.value?.userName
        val currentUserImage = userData.value?.imageUrl

        if(currentUid != null){
            val postUuid = UUID.randomUUID().toString()
            val post = PostData(
                postId = postUuid,
                userId = currentUid,
                username = currentUsername,
                userImage = currentUserImage,
                postImage = imageUri.toString(),
                postDescription = description,
                time = System.currentTimeMillis()
            )

            db.collection(POSTS).document(postUuid).set(post)
                .addOnSuccessListener {
                    popupNotification.value = Event("Post successfully created")
                    inProgress.value = false
                    onPostSuccess.invoke()
                }
                .addOnFailureListener { exc ->
                    handleException(exc, "Unable to create post")
                    inProgress.value = false
                }
        }
        else{
            handleException(customMessage = "Error: username unavailable, unable to create post")
            onLogout() //since we don't have the valid data, we'll perform the logout process and reset the whole process
            inProgress.value = false
        }
    }
}