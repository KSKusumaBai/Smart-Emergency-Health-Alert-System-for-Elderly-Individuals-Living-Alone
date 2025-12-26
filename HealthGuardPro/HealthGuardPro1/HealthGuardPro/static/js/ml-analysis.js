// Machine Learning health data analysis
class MLHealthAnalyzer {
    constructor() {
        this.model = null;
        this.thresholds = this.loadThresholds();
        this.historicalData = [];
        this.initializeModel();
    }

    loadThresholds() {
        // Load custom thresholds from settings or use defaults
        const saved = localStorage.getItem('healthThresholds');
        if (saved) {
            return JSON.parse(saved);
        }
        
        return {
            rest: {
                heartRate: { min: 60, max: 100 },
                bloodPressure: { min: 90, max: 140 }
            },
            active: {
                heartRate: { min: 100, max: 150 },
                bloodPressure: { min: 100, max: 160 }
            },
            light: {
                heartRate: { min: 80, max: 120 },
                bloodPressure: { min: 95, max: 150 }
            },
            moderate: {
                heartRate: { min: 100, max: 140 },
                bloodPressure: { min: 100, max: 160 }
            },
            sleep: {
                heartRate: { min: 50, max: 80 },
                bloodPressure: { min: 90, max: 130 }
            },
            temperature: { min: 97.0, max: 99.5 }
        };
    }

    async initializeModel() {
        try {
            // For demo purposes, we'll use a simple rule-based system
            // In production, you would load a pre-trained TensorFlow.js model
            console.log('ML Health Analyzer initialized');
            this.model = 'rule-based'; // Placeholder
        } catch (error) {
            console.error('Failed to initialize ML model:', error);
        }
    }

    analyzeHealthData(data) {
        // Add current data to historical tracking
        this.historicalData.push({
            ...data,
            timestamp: Date.now()
        });

        // Keep only last 100 readings
        if (this.historicalData.length > 100) {
            this.historicalData.shift();
        }

        // Perform comprehensive analysis
        const basicAnalysis = this.performBasicAnalysis(data);
        const trendAnalysis = this.performTrendAnalysis();
        const riskAssessment = this.assessRisk(data, basicAnalysis, trendAnalysis);

        return {
            status: riskAssessment.level,
            message: riskAssessment.message,
            details: {
                basic: basicAnalysis,
                trend: trendAnalysis,
                risk: riskAssessment
            }
        };
    }

    performBasicAnalysis(data) {
        const activityThresholds = this.thresholds[data.activityState] || this.thresholds.rest;
        const results = {
            heartRate: 'normal',
            bloodPressure: 'normal',
            temperature: 'normal',
            overall: 'normal'
        };

        // Heart Rate Analysis
        if (data.heartRate < activityThresholds.heartRate.min) {
            results.heartRate = 'low';
        } else if (data.heartRate > activityThresholds.heartRate.max) {
            results.heartRate = 'high';
        }

        // Blood Pressure Analysis
        const bpSystolic = data.bloodPressure.systolic;
        const bpDiastolic = data.bloodPressure.diastolic;
        
        if (bpSystolic < activityThresholds.bloodPressure.min || bpDiastolic < 60) {
            results.bloodPressure = 'low';
        } else if (bpSystolic > activityThresholds.bloodPressure.max || bpDiastolic > 90) {
            results.bloodPressure = 'high';
        }

        // Temperature Analysis
        if (data.temperature < this.thresholds.temperature.min) {
            results.temperature = 'low';
        } else if (data.temperature > this.thresholds.temperature.max) {
            results.temperature = 'high';
        }

        // Overall assessment
        const abnormalParameters = Object.values(results).filter(v => v !== 'normal').length;
        if (abnormalParameters >= 2) {
            results.overall = 'abnormal';
        } else if (abnormalParameters === 1) {
            results.overall = 'warning';
        }

        return results;
    }

    performTrendAnalysis() {
        if (this.historicalData.length < 5) {
            return { trend: 'insufficient_data', confidence: 0 };
        }

        const recent = this.historicalData.slice(-10);
        const older = this.historicalData.slice(-20, -10);

        if (older.length === 0) {
            return { trend: 'stable', confidence: 0.5 };
        }

        // Calculate trends for key metrics
        const trends = {
            heartRate: this.calculateTrend(older.map(d => d.heartRate), recent.map(d => d.heartRate)),
            systolic: this.calculateTrend(
                older.map(d => d.bloodPressure.systolic), 
                recent.map(d => d.bloodPressure.systolic)
            ),
            temperature: this.calculateTrend(
                older.map(d => d.temperature), 
                recent.map(d => d.temperature)
            )
        };

        // Determine overall trend
        const trendValues = Object.values(trends);
        const averageTrend = trendValues.reduce((sum, t) => sum + t, 0) / trendValues.length;
        
        let overallTrend = 'stable';
        if (averageTrend > 0.1) overallTrend = 'increasing';
        else if (averageTrend < -0.1) overallTrend = 'decreasing';

        return {
            trend: overallTrend,
            details: trends,
            confidence: Math.min(this.historicalData.length / 20, 1)
        };
    }

    calculateTrend(oldValues, newValues) {
        const oldAvg = oldValues.reduce((sum, val) => sum + val, 0) / oldValues.length;
        const newAvg = newValues.reduce((sum, val) => sum + val, 0) / newValues.length;
        
        return (newAvg - oldAvg) / oldAvg; // Percentage change
    }

    assessRisk(data, basicAnalysis, trendAnalysis) {
        let riskLevel = 'normal';
        let message = 'All parameters within normal range';
        let riskFactors = [];

        // Check for immediate critical conditions
        if (data.heartRate > 180 || data.heartRate < 40) {
            riskLevel = 'danger';
            riskFactors.push('Critical heart rate');
        }

        if (data.bloodPressure.systolic > 180 || data.bloodPressure.systolic < 70) {
            riskLevel = 'danger';
            riskFactors.push('Critical blood pressure');
        }

        if (data.temperature > 102 || data.temperature < 95) {
            riskLevel = 'danger';
            riskFactors.push('Critical temperature');
        }

        // Check for warning conditions
        if (riskLevel === 'normal') {
            if (basicAnalysis.overall === 'abnormal') {
                riskLevel = 'warning';
                riskFactors.push('Multiple parameters abnormal');
            } else if (basicAnalysis.heartRate !== 'normal') {
                riskLevel = 'warning';
                riskFactors.push(`Heart rate ${basicAnalysis.heartRate}`);
            } else if (basicAnalysis.bloodPressure !== 'normal') {
                riskLevel = 'warning';
                riskFactors.push(`Blood pressure ${basicAnalysis.bloodPressure}`);
            } else if (basicAnalysis.temperature !== 'normal') {
                riskLevel = 'warning';
                riskFactors.push(`Temperature ${basicAnalysis.temperature}`);
            }
        }

        // Factor in trends
        if (trendAnalysis.trend === 'increasing' && trendAnalysis.confidence > 0.7) {
            if (riskLevel === 'normal') {
                riskLevel = 'warning';
                riskFactors.push('Increasing trend detected');
            } else if (riskLevel === 'warning') {
                riskLevel = 'danger';
                riskFactors.push('Worsening trend');
            }
        }

        // Generate appropriate message
        if (riskLevel === 'danger') {
            message = `EMERGENCY: ${riskFactors.join(', ')}. Seek immediate medical attention.`;
        } else if (riskLevel === 'warning') {
            message = `Warning: ${riskFactors.join(', ')}. Monitor closely.`;
        } else {
            message = 'All parameters normal';
        }

        return {
            level: riskLevel,
            message: message,
            factors: riskFactors,
            confidence: trendAnalysis.confidence
        };
    }

    // Pattern recognition for specific conditions
    detectArrhythmia(heartRateHistory) {
        if (heartRateHistory.length < 10) return false;
        
        // Simple arrhythmia detection based on heart rate variability
        const variance = this.calculateVariance(heartRateHistory.slice(-10));
        return variance > 400; // High variability threshold
    }

    detectHypertensiveCrisis(systolicHistory) {
        if (systolicHistory.length < 3) return false;
        
        // Check for sustained high blood pressure
        const recent = systolicHistory.slice(-3);
        return recent.every(bp => bp > 180);
    }

    calculateVariance(values) {
        const mean = values.reduce((sum, val) => sum + val, 0) / values.length;
        const squaredDiffs = values.map(val => Math.pow(val - mean, 2));
        return squaredDiffs.reduce((sum, val) => sum + val, 0) / values.length;
    }

    // Advanced ML prediction (placeholder for future implementation)
    async predictHealthEvents(data) {
        // This would use a trained model to predict potential health events
        // For now, return mock predictions
        return {
            riskLevel: 'low',
            timeToEvent: null,
            confidence: 0.85,
            recommendations: [
                'Continue regular monitoring',
                'Maintain current activity level',
                'Stay hydrated'
            ]
        };
    }

    // Generate health insights
    generateInsights() {
        if (this.historicalData.length < 10) {
            return ['Insufficient data for insights. Continue monitoring.'];
        }

        const insights = [];
        const recent = this.historicalData.slice(-20);
        
        // Average heart rate insight
        const avgHeartRate = recent.reduce((sum, d) => sum + d.heartRate, 0) / recent.length;
        if (avgHeartRate > 85) {
            insights.push('Your average heart rate is elevated. Consider stress management techniques.');
        } else if (avgHeartRate < 65) {
            insights.push('Your heart rate is well within healthy range. Great cardiovascular health!');
        }

        // Blood pressure trend
        const bpTrend = this.calculateTrend(
            recent.slice(0, 10).map(d => d.bloodPressure.systolic),
            recent.slice(-10).map(d => d.bloodPressure.systolic)
        );
        
        if (bpTrend > 0.05) {
            insights.push('Blood pressure showing upward trend. Monitor salt intake and stress levels.');
        }

        // Activity-based insights
        const activities = recent.map(d => d.activityState);
        const restRatio = activities.filter(a => a === 'rest' || a === 'sleep').length / activities.length;
        
        if (restRatio > 0.8) {
            insights.push('Consider increasing physical activity for better cardiovascular health.');
        }

        return insights.length > 0 ? insights : ['Your health parameters are stable. Keep up the good work!'];
    }
}

// Global ML analyzer instance
window.mlAnalyzer = new MLHealthAnalyzer();

// Initialize when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    console.log('ML Health Analyzer ready');
});
