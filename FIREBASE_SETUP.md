# Firebase Backend Setup Guide

This guide explains how to set up the Firebase Cloud Function backend for VoiceOverlay app. This enables secure API key storage and prepares the app for Play Store publication with subscription support.

## Why Firebase Backend?

- **Security**: API key stays on the server, not in the APK
- **Subscription Support**: Can verify Google Play subscriptions before processing
- **Cost Control**: Implement rate limiting and usage monitoring
- **Serverless**: No need to manage servers, Firebase handles scaling

## Prerequisites

1. A Firebase account (free tier is sufficient for testing)
2. Node.js 18+ installed on your development machine
3. Firebase CLI installed: `npm install -g firebase-tools`

## Step-by-Step Setup

### 1. Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add Project"
3. Enter project name (e.g., "voiceoverlay-app")
4. Disable Google Analytics (optional for testing)
5. Click "Create Project"

### 2. Initialize Firebase Functions

On your development machine (not Termux), in the `firebase-function` directory:

```bash
cd /path/to/VoiceOverlay/firebase-function

# Login to Firebase
firebase login

# Initialize Firebase project
firebase init functions

# When prompted:
# - Select existing project: voiceoverlay-app
# - Language: JavaScript
# - ESLint: No (optional)
# - Install dependencies: Yes
```

This will create a Firebase configuration and install dependencies.

### 3. Copy Function Code

The function code is already in `index.js` and `package.json`. If you initialized a new project, make sure to:

1. Replace the content of `functions/index.js` with the provided `index.js`
2. Update `functions/package.json` dependencies to match the provided `package.json`
3. Run `npm install` in the `functions` directory

### 4. Configure OpenAI API Key

Set your OpenAI API key as a Firebase environment variable (keeps it secure):

```bash
firebase functions:config:set openai.key="YOUR_OPENAI_API_KEY_HERE"
```

Verify it was set:
```bash
firebase functions:config:get
```

### 5. Deploy the Function

```bash
# Deploy to Firebase
firebase deploy --only functions

# Expected output:
# ✔  functions[transcribeAudio(us-central1)]: Successful create operation.
# Function URL: https://us-central1-PROJECT_ID.cloudfunctions.net/transcribeAudio
```

**Important**: Copy the Function URL from the output!

### 6. Update FirebaseWhisperAPI.java

Edit `/data/data/com.termux/files/home/VoiceOverlay/src/com/voiceoverlay/FirebaseWhisperAPI.java`:

```java
// Replace this line:
private static final String FIREBASE_FUNCTION_URL = "https://YOUR_REGION-YOUR_PROJECT_ID.cloudfunctions.net/transcribeAudio";

// With your actual function URL:
private static final String FIREBASE_FUNCTION_URL = "https://us-central1-voiceoverlay-app.cloudfunctions.net/transcribeAudio";
```

### 7. Rebuild the App

```bash
cd /data/data/com.termux/files/home/VoiceOverlay
bash build.sh
```

### 8. Test the Firebase Mode

1. Install the rebuilt APK on your device
2. Open VoiceOverlay settings
3. Go to Transcription Settings
4. Set "Transcription Mode" to "Firebase Backend"
5. Save settings
6. Test recording and transcription

The app will now use the Firebase backend instead of direct API calls!

## Firebase Function Features

### Current Features

- ✅ Secure API key storage on server
- ✅ Multipart form data handling for audio files
- ✅ Support for custom transcription prompts
- ✅ Support for multiple Whisper models
- ✅ Error handling and proper HTTP status codes
- ✅ CORS enabled for testing

### TODO: Future Enhancements

These features are commented in the code but not yet implemented:

1. **Subscription Verification**
   - Integrate with Google Play Billing API
   - Verify purchase tokens
   - Check subscription status before processing

2. **Rate Limiting**
   - Prevent abuse by limiting requests per user
   - Track usage in Firebase Firestore

3. **Usage Analytics**
   - Log transcription counts
   - Monitor costs and performance

4. **User Authentication**
   - Firebase Authentication integration
   - Secure user identification

## Cost Considerations

### Firebase Costs (Free Tier)

- **Invocations**: 2M/month (free)
- **Compute Time**: 400,000 GB-seconds/month (free)
- **Egress**: 5GB/month (free)

For a typical transcription (5-10 seconds audio):
- ~1-3 seconds compute time
- ~200KB data transfer

**Estimated**: ~10,000-20,000 transcriptions/month on free tier

### OpenAI Costs

- **Whisper API**: $0.006/minute of audio
- 10 second clip = ~$0.001
- 1000 transcriptions = ~$1

### Total Monthly Costs (Example)

- 1000 active users
- 100 transcriptions/user/month
- 10 second average audio

**Total**: ~$100/month OpenAI + $0 Firebase (within free tier)

**Subscription Revenue**: 1000 users × $2.99/month = $2,990/month

**Profit**: ~$2,890/month (before Play Store fees)

## Monitoring and Debugging

### View Function Logs

```bash
firebase functions:log
```

### View Logs in Console

1. Go to Firebase Console → Functions
2. Click on `transcribeAudio` function
3. View logs, metrics, and errors

### Test Locally

```bash
# Start Firebase emulator
firebase emulators:start --only functions

# Function will be available at:
# http://localhost:5001/PROJECT_ID/us-central1/transcribeAudio
```

## Security Best Practices

1. **Never commit API keys** to version control
2. **Enable Firebase App Check** before production (prevents unauthorized access)
3. **Implement rate limiting** to prevent abuse
4. **Verify subscriptions** before processing requests
5. **Monitor usage** for suspicious activity
6. **Set up billing alerts** in Google Cloud Console

## Troubleshooting

### "Server configuration error"

- API key not set: Run `firebase functions:config:set openai.key="YOUR_KEY"`
- Redeploy: `firebase deploy --only functions`

### "CORS error" in app

- CORS is enabled in the function, but check Firebase Console for any errors
- Ensure function is deployed and accessible

### "Request timeout"

- Increase timeout in FirebaseWhisperAPI.java (currently 120s)
- Check Firebase function timeout (default 60s, can increase to 540s)

### "OpenAI API error"

- Check Firebase logs: `firebase functions:log`
- Verify OpenAI API key is valid and has credits
- Check OpenAI API status page

## Next Steps

After Firebase is working:

1. **Remove Direct API Mode**: Update MainActivity to hide the API key mode
2. **Add Subscription**: Integrate Google Play Billing Library
3. **Update Firebase Function**: Add subscription verification logic
4. **Test Thoroughly**: Test subscription flows and edge cases
5. **Publish to Play Store**: Submit app for review

## Additional Resources

- [Firebase Functions Documentation](https://firebase.google.com/docs/functions)
- [OpenAI Whisper API](https://platform.openai.com/docs/guides/speech-to-text)
- [Google Play Billing](https://developer.android.com/google/play/billing)
- [Firebase Security Rules](https://firebase.google.com/docs/rules)

## Support

If you encounter issues:

1. Check Firebase Console logs
2. Check `firebase functions:log`
3. Verify API key configuration
4. Test with Firebase emulator first
5. Check OpenAI API status and credits

---

**Note**: For now, both "Direct API" and "Firebase Backend" modes are available in settings. This allows you to test both approaches. Once Firebase is fully tested, you can remove the Direct API mode for production release.
