package com.example.instagramclone

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.instagramclone.data.Event
import com.example.instagramclone.data.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

const val USERS = "users"

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

    fun onSignup(username: String, email: String, pass: String) {
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

    }

    fun handleException(exception: Exception? = null, customMessage: String = ""){
        exception?.printStackTrace()
        val errorMessage = exception?.localizedMessage ?: ""
        val message = if (customMessage.isEmpty()) errorMessage else "$customMessage : $errorMessage"
        popupNotification.value = Event(message)
    }
}