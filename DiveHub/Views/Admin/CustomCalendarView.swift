//
//  CustomCalendarView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct CustomCalendarView: View {
    @Binding var selectedDate: Date
    let bookings: [Booking]
    @StateObject private var localizationService = LocalizationService.shared
    
    @State private var currentMonth: Date = Date()
    
    private let calendar = Calendar.current
    var body: some View {
        VStack(spacing: 16) {
            // Month Navigation
            HStack {
                Button(action: { changeMonth(-1) }) {
                    Image(systemName: "chevron.left")
                        .foregroundColor(.divePrimary)
                }
                
                Spacer()
                
                Text(monthTitle)
                    .font(.headline)
                    .fontWeight(.semibold)
                
                Spacer()
                
                Button(action: { changeMonth(1) }) {
                    Image(systemName: "chevron.right")
                        .foregroundColor(.divePrimary)
                }
            }
            .padding(.horizontal)
            
            // Weekday Headers
            HStack(spacing: 0) {
                ForEach(weekdaySymbols, id: \.self) { weekday in
                    Text(weekday)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal)
            
            // Calendar Grid
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 8) {
                ForEach(Array(daysInMonth.enumerated()), id: \.offset) { _, date in
                    if let date = date {
                        DayCell(
                            date: date,
                            isSelected: calendar.isDate(date, inSameDayAs: selectedDate),
                            isToday: calendar.isDate(date, inSameDayAs: Date()),
                            bookingStatus: getBookingStatus(for: date),
                            onTap: {
                                selectedDate = date
                            }
                        )
                    } else {
                        Color.clear
                            .frame(height: 44)
                    }
                }
            }
            .padding(.horizontal)
            
            // Legend
            HStack(spacing: 16) {
                LegendItem(color: .orange, label: localizationService.localizedString("bookingStatusPending", table: "admin"))
                LegendItem(color: .blue, label: localizationService.localizedString("bookingStatusConfirmed", table: "admin"))
                LegendItem(color: .green, label: localizationService.localizedString("bookingStatusCompleted", table: "admin"))
                LegendItem(color: .red, label: localizationService.localizedString("bookingStatusCancelled", table: "admin"))
            }
            .font(.caption)
            .padding(.horizontal)
        }
    }

    private var monthTitle: String {
        let formatter = DateFormatter()
        formatter.locale = localeForLanguage
        formatter.setLocalizedDateFormatFromTemplate("LLLL yyyy")
        return formatter.string(from: currentMonth)
    }

    private var localeForLanguage: Locale {
        switch localizationService.currentLanguage {
        case .chinese:
            return Locale(identifier: "zh_Hans")
        default:
            return Locale(identifier: localizationService.currentLanguage.rawValue)
        }
    }
    
    private var weekdaySymbols: [String] {
        let formatter = DateFormatter()
        formatter.locale = localeForLanguage
        return formatter.shortWeekdaySymbols
    }
    
    private var daysInMonth: [Date?] {
        guard let firstDayOfMonth = calendar.dateInterval(of: .month, for: currentMonth)?.start else {
            return []
        }
        
        let firstDayWeekday = calendar.component(.weekday, from: firstDayOfMonth)
        let numberOfDaysInMonth = calendar.range(of: .day, in: .month, for: currentMonth)?.count ?? 0
        
        var days: [Date?] = []
        
        // Add empty cells for days before the first day of the month
        let startOffset = (firstDayWeekday - calendar.firstWeekday + 7) % 7
        for _ in 0..<startOffset {
            days.append(nil)
        }
        
        // Add days of the month
        for day in 1...numberOfDaysInMonth {
            if let date = calendar.date(byAdding: .day, value: day - 1, to: firstDayOfMonth) {
                days.append(date)
            }
        }
        
        return days
    }
    
    private func changeMonth(_ direction: Int) {
        if let newMonth = calendar.date(byAdding: .month, value: direction, to: currentMonth) {
            currentMonth = newMonth
        }
    }
    
    private func getBookingStatus(for date: Date) -> BookingStatusIndicator? {
        let dayBookings = bookings.filter { calendar.isDate($0.date, inSameDayAs: date) }
        
        guard !dayBookings.isEmpty else { return nil }
        
        // Determine the primary status for the day
        // Priority: cancelled > pending > quoted > confirmed > completed
        if dayBookings.contains(where: { $0.status == .cancelled }) {
            return .cancelled
        } else if dayBookings.contains(where: { $0.status == .pending }) {
            return .pending
        } else if dayBookings.contains(where: { $0.status == .quoted }) {
            return .quoted
        } else if dayBookings.contains(where: { $0.status == .confirmed }) {
            return .confirmed
        } else if dayBookings.contains(where: { $0.status == .completed }) {
            return .completed
        }
        
        return nil
    }
    
    enum BookingStatusIndicator {
        case pending
        case quoted
        case confirmed
        case completed
        case cancelled
        
        var color: Color {
            switch self {
            case .pending: return .orange
            case .quoted: return .purple
            case .confirmed: return .blue
            case .completed: return .green
            case .cancelled: return .red
            }
        }
    }
}

struct DayCell: View {
    let date: Date
    let isSelected: Bool
    let isToday: Bool
    let bookingStatus: CustomCalendarView.BookingStatusIndicator?
    let onTap: () -> Void
    
    private let calendar = Calendar.current
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 4) {
                Text("\(calendar.component(.day, from: date))")
                    .font(.system(size: 16, weight: isSelected ? .bold : .regular))
                    .foregroundColor(isSelected ? .white : (isToday ? .divePrimary : .primary))
                
                if let status = bookingStatus {
                    Circle()
                        .fill(status.color)
                        .frame(width: 6, height: 6)
                } else {
                    Circle()
                        .fill(Color.clear)
                        .frame(width: 6, height: 6)
                }
            }
            .frame(width: 44, height: 44)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(isSelected ? Color.divePrimary : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isToday && !isSelected ? Color.divePrimary : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }
}

struct LegendItem: View {
    let color: Color
    let label: String
    
    var body: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(label)
                .foregroundColor(.secondary)
        }
    }
}

#Preview {
    CustomCalendarView(
        selectedDate: .constant(Date()),
        bookings: []
    )
}
