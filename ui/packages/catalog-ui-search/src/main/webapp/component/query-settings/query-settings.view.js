/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
/*global define, setTimeout*/
define([
    'marionette',
    'backbone',
    'underscore',
    'jquery',
    './query-settings.hbs',
    'js/CustomElements',
    'js/store',
    'js/model/QuerySchedule',
    'component/dropdown/dropdown',
    'component/dropdown/query-src/dropdown.query-src.view',
    'component/property/property.view',
    'component/property/property',
    'component/singletons/user-instance',
    'component/sort/sort.view',
    'component/query-schedule/query-schedule.view',
    'js/Common',
    'component/result-form/result-form'
], function (Marionette, Backbone, _, $, template, CustomElements, store, QueryScheduleModel, DropdownModel,
            QuerySrcView, PropertyView, Property, user, SortItemCollectionView, ScheduleQueryView, Common, ResultForm) {

    return Marionette.LayoutView.extend({
        template: template,
        tagName: CustomElements.register('query-settings'),
        modelEvents: {},
        events: {
            'click .editor-edit': 'turnOnEditing',
            'click .editor-cancel': 'cancel',
            'click .editor-save': 'save',
            'click .editor-saveRun': 'run'
        },
        regions: {
            settingsSortField: '.settings-sorting-field',
            settingsSrc: '.settings-src',
            settingsSchedule: '.settings-scheduling',
            resultForm: '.result-form'

        },
        ui: {},
        focus: function () {
        },
        initialize: function () {
            this.model = this.model._cloneOf ? store.getQueryById(this.model._cloneOf) : this.model;
            this.listenTo(this.model, 'change:sortField change:sortOrder change:src change:federation', Common.safeCallback(this.onBeforeShow));
            this.resultFormCollection = ResultForm.getResultCollection();
            this.listenTo(this.resultFormCollection, 'change:added', this.handleFormUpdate)
        },
        handleFormUpdate: function(newForm) {
            this.renderResultForms(this.resultFormCollection.filteredList)
        },
        onBeforeShow: function () {
            this.setupSortFieldDropdown();
            this.setupSrcDropdown();
            this.setupScheduling();
            this.turnOnEditing();
            this.renderResultForms(this.resultFormCollection.filteredList)
        },
        renderResultForms: function(resultTemplates){
            if(resultTemplates == undefined)
            {
                resultTemplates = [];
            }
            resultTemplates.push({
                label: 'All Fields',
                value: 'All Fields',
                id: 'All Fields',
                descriptors: [],
                description: 'All Fields'
            });
            resultTemplates =  _.uniq(resultTemplates, 'id');
            let lastIndex = resultTemplates.length - 1;
            let detailLevelProperty = new Property({
                label: 'Result Form',
                enum: resultTemplates,
                value: [this.model.get('detail-level') || (resultTemplates && resultTemplates[lastIndex] && resultTemplates[lastIndex].value)],
                showValidationIssues: false,
                id: 'Result Form'
            });
            this.listenTo(detailLevelProperty, 'change:value', this.handleChangeDetailLevel);
            this.resultForm.show(new PropertyView({
                model: detailLevelProperty
            }));
            this.resultForm.currentView.turnOnEditing();
        },
        handleChangeDetailLevel: function (model, values) {
            $.each(model.get('enum'), (function (index, value) {
                if (values[0] === value.value) {
                    this.model.set('detail-level', value);
                }
            }).bind(this));
        },
        onRender: function () {
            this.setupSrcDropdown();
        },
        setupSortFieldDropdown: function () {
            this.settingsSortField.show(new SortItemCollectionView({
                collection: new Backbone.Collection(this.model.get('sorts')),
                showBestTextOption: true
            }));
        },
        setupSrcDropdown: function () {
            var sources = this.model.get('src');
            this._srcDropdownModel = new DropdownModel({
                value: sources ? sources : [],
                federation: this.model.get('federation')
            });
            this.settingsSrc.show(new QuerySrcView({
                model: this._srcDropdownModel
            }));
            this.settingsSrc.currentView.turnOffEditing();
        },
        turnOffEditing: function () {
            this.$el.removeClass('is-editing');
            this.regionManager.forEach(function (region) {
                if (region.currentView && region.currentView.turnOffEditing) {
                    region.currentView.turnOffEditing();
                }
            });
        },
        turnOnEditing: function(){
            this.$el.addClass('is-editing');
            this.regionManager.forEach(function(region){
                 if (region.currentView && region.currentView.turnOnEditing){
                     region.currentView.turnOnEditing();
                 }
            });
            this.focus();
        },
        setupScheduling: function() {
            let username = user.get('user').get('userid');
            let scheduleModel = this.model.get('schedules').get(username);
            let deliveryModel = this.model.get('deliveries').get(username);
            if (scheduleModel === undefined) {
                scheduleModel = new QueryScheduleModel({ userId: username });
            }
            this.settingsSchedule.show(new ScheduleQueryView({
                model: scheduleModel,
                deliveryModel: deliveryModel
            }));
            this.settingsSchedule.currentView.turnOffEditing();
        },
        cancel: function () {
            this.$el.removeClass('is-editing');
            this.onBeforeShow();
            this.$el.trigger('closeDropdown.' + CustomElements.getNamespace());
        },
        toJSON: function () {
            var federation = this._srcDropdownModel.get('federation');
            var src;
            if (federation === 'selected') {
                src = this._srcDropdownModel.get('value');
                if (src === undefined || src.length === 0) {
                    federation = 'local';
                }
            }
            const sorts = this.settingsSortField.currentView.collection.toJSON();
            let detailLevel = this.resultForm.currentView && this.resultForm.currentView.model.get('value')[0]
            if (detailLevel && detailLevel === 'All Fields') {
                detailLevel = undefined;
            }
            const scheduleModel = this.settingsSchedule.currentView.getSchedulingConfiguration();
            const deliveryModel = this.settingsSchedule.currentView.getDeliveryConfiguration();
            this.model.get('schedules').add(scheduleModel, {merge: true});
            this.model.get('deliveries').add(deliveryModel, {merge: true});
            return {
                src: src,
                federation: federation,
                sorts: sorts,
                'detail-level': detailLevel,
                schedules: this.model.get('schedules'),
                deliveries: this.model.get('deliveries')
            };
        },
        saveToModel: function () {
            this.model.set(this.toJSON());
        },
        save: function () {
            this.saveToModel();
            this.cancel();
            this.$el.trigger('closeDropdown.' + CustomElements.getNamespace());
        },
        run: function () {
            this.saveToModel();
            this.cancel();
            this.model.startSearch();
            store.setCurrentQuery(this.model);
            this.$el.trigger('closeDropdown.' + CustomElements.getNamespace());
        }
    });
});
