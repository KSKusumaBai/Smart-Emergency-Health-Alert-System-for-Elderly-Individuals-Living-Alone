# TODO List for HealthGuardPro Enhancements

## 1. Add 5-minute interval vital simulation (normal & abnormal)
- [ ] Modify main.js to change simulation interval from 2 seconds to 5 minutes (300000 ms)
- [ ] Add two buttons on dashboard: "Simulate Normal Data" and "Simulate Abnormal Data"
- [ ] Implement logic to generate normal vs abnormal vitals based on button pressed
- [ ] Ensure simulation updates dashboard metrics and charts

## 2. Improve UI of dashboard
- [ ] Enhance color scheme and styling in dashboard.html
- [ ] Improve layout responsiveness
- [ ] Add better icons and visual indicators
- [ ] Optimize metric cards and charts display

## 3. Create a separate Alerts page
- [ ] Create new template: templates/alerts.html
- [ ] Add route in app.py for /alerts
- [ ] Display recent alerts, emergency history
- [ ] Add navigation link to alerts page from dashboard

## 4. Ensure SOS + abnormal alert routing works with simulation
- [ ] Modify simulation to trigger SOS automatically after 5 seconds if abnormal data is simulated
- [ ] Integrate with existing AlertManager for abnormal data handling
- [ ] Test end-to-end flow: abnormal simulation -> dashboard update -> auto SOS trigger
- [ ] Verify SMS notifications are sent for abnormal alerts

## Testing and Verification
- [ ] Test normal simulation button
- [ ] Test abnormal simulation button and auto SOS
- [ ] Verify alerts page displays correctly
- [ ] Check UI improvements on different screen sizes
- [ ] Ensure backward compatibility with existing features
