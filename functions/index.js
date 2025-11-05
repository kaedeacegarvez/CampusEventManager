const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendNewEventNotification = functions.firestore
    .document("events/{eventId}")
    .onCreate(async (snap, context) => {
      const newEvent = snap.data();

      console.log("New event created: " + newEvent.title);

      const usersSnapshot = await admin.firestore()
          .collection("users")
          .where("role", "==", "student")
          .get();

      if (usersSnapshot.empty) {
        console.log("No students found");
        return;
      }

      const tokens = [];
      const tokenMap = {}; // To map tokens back to user IDs for cleanup
      usersSnapshot.forEach((doc) => {
        const user = doc.data();
        if (user.fcmToken) {
          tokens.push(user.fcmToken);
          tokenMap[user.fcmToken] = doc.id; // Map token to user's doc ID
        }
      });

      if (tokens.length === 0) {
        console.log("No tokens found");
        return;
      }

      const payload = {
        notification: {
          title: "New Event: " + newEvent.title,
          body: `Check out the new event on ${newEvent.date} at ${newEvent.venue}!`,
        },
      };

      try {
        const response = await admin.messaging().sendToDevice(tokens, payload);
        console.log("Sent notifications to " + tokens.length + " students");

        // --- Start of Token Cleanup Logic ---
        const tokensToRemove = [];
        response.results.forEach((result, index) => {
          const error = result.error;
          if (error) {
            console.error("Failure sending notification to", tokens[index], error);
            // Check if the error is due to an invalid token
            if (
              error.code === "messaging/invalid-registration-token" ||
              error.code === "messaging/registration-token-not-registered"
            ) {
              const invalidToken = tokens[index];
              const userId = tokenMap[invalidToken];
              console.log(`Found invalid token for user ${userId}. Scheduling for removal.`);
              // Schedule the invalid token field for deletion from the user's document
              tokensToRemove.push(
                  admin.firestore().collection("users").doc(userId).update({
                    fcmToken: admin.firestore.FieldValue.delete(),
                  }),
              );
            }
          }
        });

        // Execute all the promises to remove the invalid tokens
        if (tokensToRemove.length > 0) {
          await Promise.all(tokensToRemove);
          console.log(`Removed ${tokensToRemove.length} invalid tokens.`);
        }
        // --- End of Token Cleanup Logic ---
      } catch (error) {
        console.error("Error sending notifications:", error);
      }
    });
