# FinTrack - Quick Start Guide

## 🎯 Project Overview

FinTrack is a comprehensive **personal financial tracking web application** built with Python Flask and SQLite. It allows you to track income and expenses across multiple categories with visual analytics.

## 📦 What's Included

### Backend
- ✅ Flask API with RESTful endpoints
- ✅ SQLite database with SQLAlchemy ORM
- ✅ Category management system
- ✅ Transaction tracking (income/expense)
- ✅ Advanced analytics (summary, trends, breakdowns)
- ✅ CORS support for cross-origin requests

### Frontend
- ✅ Responsive Bootstrap 5 UI
- ✅ Interactive charts (Chart.js)
- ✅ Real-time data updates
- ✅ Mobile-friendly design
- ✅ Intuitive transaction management interface
- ✅ Financial dashboard with key metrics

### Database
- ✅ Two main tables: Categories and Transactions
- ✅ Automatic database initialization
- ✅ Relationship management
- ✅ Cascade delete operations

## 🚀 Getting Started

### Installation (3 steps)

1. **Navigate to project directory:**
   ```bash
   cd d:\finzin\fintrack-app
   ```

2. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

3. **Run the application:**
   ```bash
   python app.py
   ```

### Access the Application

Open your browser and visit: **http://localhost:5000**

## 📱 Features Overview

### 1. **Dashboard**
   - Quick financial summary cards
   - Expense breakdown by category (pie chart)
   - Monthly income/expense trends (line chart)
   - Recent transactions table

### 2. **Transactions**
   - Add new transactions with category selection
   - Specify amount, date, and type (income/expense)
   - View all transactions in organized list
   - Delete individual transactions
   - Real-time list updates

### 3. **Categories**
   - Create unlimited custom categories
   - Assign colors for visual organization
   - Organize by icons (tagging system)
   - Manage all categories from transactions page

### 4. **Analytics**
   - Total income, expense, and balance
   - Category-wise spending breakdown
   - Monthly trends and patterns
   - Transaction count and statistics

## 🔧 Project Structure

```
fintrack-app/
├── app.py                    # Main Flask application
├── requirements.txt          # Python dependencies
├── .env                      # Environment configuration
├── .gitignore               # Git ignore rules
├── README.md                # Full documentation
├── QUICKSTART.md            # This file
│
├── templates/               # HTML templates
│   ├── base.html           # Base template with navbar
│   ├── index.html          # Home/welcome page
│   ├── dashboard.html      # Analytics dashboard
│   └── transactions.html   # Transaction management
│
├── static/                  # Static files
│   ├── css/
│   │   └── style.css       # Custom Bootstrap styling
│   └── js/
│       └── api.js          # API helper functions
│
└── instance/
    └── fintrack.db         # SQLite database (auto-created)
```

## 🌐 API Endpoints Reference

### Categories
- `GET /api/categories` - List all categories
- `POST /api/categories` - Create category
- `PUT /api/categories/<id>` - Update category
- `DELETE /api/categories/<id>` - Delete category

### Transactions
- `GET /api/transactions` - List transactions
- `POST /api/transactions` - Create transaction
- `PUT /api/transactions/<id>` - Update transaction
- `DELETE /api/transactions/<id>` - Delete transaction

### Analytics
- `GET /api/analytics/summary` - Financial summary
- `GET /api/analytics/category-breakdown` - Spending by category
- `GET /api/analytics/monthly` - Monthly trends

## 💾 Database Setup

The database is **automatically created** on first run. No manual setup required!

### Database Files
- Location: `instance/fintrack.db`
- Type: SQLite3
- Tables: 2 (Category, Transaction)

## 🎨 Customization Options

### Change Port
Edit `app.py` line ~190:
```python
app.run(debug=True, host='0.0.0.0', port=8000)  # Change 5000 to any port
```

### Modify Theme Colors
Edit `static/css/style.css` CSS variables:
```css
:root {
    --primary-color: #2c3e50;      /* Change these colors */
    --secondary-color: #3498db;
    --success-color: #27ae60;
    /* etc... */
}
```

### Add New Categories
Via UI: Navigate to Transactions → Manage Categories
Via Code: Use POST `/api/categories` endpoint

## 📊 Sample Data for Testing

### Quick Test Setup

1. **Create Categories:**
   - Food (🍔)
   - Transportation (🚗)
   - Entertainment (🎬)
   - Salary (💼)

2. **Add Sample Transactions:**
   - Income: Salary - $2,000.00
   - Expense: Groceries - $150.00
   - Expense: Gas - $40.00
   - Expense: Movie - $15.00

3. **View Analytics:**
   - Dashboard will show breakdown and trends

## 🔍 Troubleshooting

### Issue: Port 5000 already in use
```bash
# Find process using port 5000
netstat -ano | findstr :5000

# Kill the process (replace PID with actual number)
taskkill /PID <PID> /F
```

### Issue: Module not found error
```bash
# Reinstall dependencies
pip install --upgrade pip
pip install -r requirements.txt
```

### Issue: Database errors
```bash
# Delete database and restart (will reset all data)
del instance\fintrack.db
python app.py
```

## 📈 Next Steps

1. **Add Transactions** - Start tracking your finances
2. **Create Categories** - Organize by spending patterns
3. **Review Dashboard** - Analyze spending trends
4. **Export Data** - Use API endpoints for data export

## 🔐 Security Notes

- ⚠️ Development server only - not for production
- ⚠️ No authentication implemented yet
- ⚠️ Debug mode is enabled - disable in production
- ✅ CORS enabled for local development

## 📚 Advanced Features Available

Ready for expansion:
- User authentication
- Multi-user support
- Cloud synchronization
- Mobile app integration
- Budget planning
- Recurring transactions
- Receipt scanning (OCR)
- Multi-currency support

## 📞 Support

For detailed documentation: See `README.md`
For API details: See `README.md` - API Endpoints section

---

**Start tracking your finances today with FinTrack!** 💰📊
